package com.islandride.identityaccess.service;

import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.security.JwtTokenProvider;
import com.islandride.core.security.RefreshTokenService;
import com.islandride.core.security.UserPrincipal;
import com.islandride.identityaccess.domain.*;
import com.islandride.identityaccess.dto.*;
import com.islandride.identityaccess.repository.PasswordResetTokenRepository;
import com.islandride.identityaccess.repository.RoleRepository;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.partner.repository.PartnerStaffRepository;
import com.islandride.usermanagement.domain.TouristProfile;
import com.islandride.usermanagement.repository.TouristProfileRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TouristProfileRepository touristProfileRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PartnerStaffRepository partnerStaffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       TouristProfileRepository touristProfileRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PartnerStaffRepository partnerStaffRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.touristProfileRepository = touristProfileRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.partnerStaffRepository = partnerStaffRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.emailService = emailService;
    }

    public static record AuthResult(AuthResponse response, String rawRefreshToken) {}

    @Transactional
    public UserDto registerTourist(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().trim().toLowerCase())) {
            throw new BusinessRuleException("An account with this email address already exists");
        }

        if (request.phone() != null && !request.phone().isBlank() && userRepository.existsByPhone(request.phone().trim())) {
            throw new BusinessRuleException("An account with this phone number already exists");
        }

        Role touristRole = roleRepository.findByName(RoleType.ROLE_TOURIST.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_TOURIST not seeded in database"));

        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone() != null ? request.phone().trim() : null);
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(touristRole);

        User savedUser = userRepository.save(user);

        // Initialize TouristProfile
        TouristProfile touristProfile = new TouristProfile();
        touristProfile.setUser(savedUser);
        touristProfileRepository.save(touristProfile);

        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFirstName());

        return toUserDto(savedUser, null);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new LockedException("User account is locked due to security policy");
        }

        if (user.getStatus() == UserStatus.DISABLED || user.getStatus() == UserStatus.SUSPENDED) {
            throw new DisabledException("User account is inactive or suspended");
        }

        Long businessId = partnerStaffRepository.findFirstByUserIdAndActiveTrueOrderByIdAsc(user.getId())
                .map(staff -> staff.getBusiness().getId())
                .orElse(null);

        UserPrincipal principal = UserPrincipal.create(user, businessId);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String rawRefreshToken = refreshTokenService.createRefreshToken(user);

        UserDto userDto = toUserDto(user, businessId);
        AuthResponse response = AuthResponse.of(accessToken, jwtTokenProvider.getAccessTokenExpirationMs(), userDto);

        return new AuthResult(response, rawRefreshToken);
    }

    @Transactional
    public AuthResult refresh(String rawOldRefreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(rawOldRefreshToken);
        User user = result.getUser();

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DISABLED || user.getStatus() == UserStatus.SUSPENDED) {
            throw new DisabledException("Account status does not permit token refresh");
        }

        Long businessId = partnerStaffRepository.findFirstByUserIdAndActiveTrueOrderByIdAsc(user.getId())
                .map(staff -> staff.getBusiness().getId())
                .orElse(null);

        UserPrincipal principal = UserPrincipal.create(user, businessId);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);

        UserDto userDto = toUserDto(user, businessId);
        AuthResponse response = AuthResponse.of(accessToken, jwtTokenProvider.getAccessTokenExpirationMs(), userDto);

        return new AuthResult(response, result.getRawNewToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeRefreshToken(rawRefreshToken);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email().trim().toLowerCase()).ifPresent(user -> {
            passwordResetTokenRepository.markAllUsedForUser(user.getId());

            String rawToken = UUID.randomUUID().toString();
            String tokenHash = RefreshTokenService.hashToken(rawToken);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(tokenHash);
            resetToken.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
            resetToken.setUsed(false);

            passwordResetTokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = RefreshTokenService.hashToken(request.token().trim());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessRuleException("Invalid or expired password reset token"));

        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Password reset token is invalid or has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("User not found"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password does not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("User not found"));

        Long businessId = partnerStaffRepository.findFirstByUserIdAndActiveTrueOrderByIdAsc(user.getId())
                .map(staff -> staff.getBusiness().getId())
                .orElse(null);

        return toUserDto(user, businessId);
    }

    public static UserDto toUserDto(User user, Long businessId) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toList());

        return new UserDto(
                user.getPublicId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getStatus().name(),
                roles,
                businessId
        );
    }
}

