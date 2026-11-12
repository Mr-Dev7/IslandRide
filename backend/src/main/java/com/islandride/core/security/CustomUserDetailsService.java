package com.islandride.core.security;

import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.partner.domain.PartnerStaff;
import com.islandride.partner.repository.PartnerStaffRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PartnerStaffRepository partnerStaffRepository;

    public CustomUserDetailsService(UserRepository userRepository,
                                    PartnerStaffRepository partnerStaffRepository) {
        this.userRepository = userRepository;
        this.partnerStaffRepository = partnerStaffRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Resolve active business membership if partner role exists
        Long businessId = partnerStaffRepository.findFirstByUserIdAndActiveTrueOrderByIdAsc(user.getId())
                .map(staff -> staff.getBusiness().getId())
                .orElse(null);

        return UserPrincipal.create(user, businessId);
    }

    @Transactional(readOnly = true)
    public UserPrincipal loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        Long businessId = partnerStaffRepository.findFirstByUserIdAndActiveTrueOrderByIdAsc(user.getId())
                .map(staff -> staff.getBusiness().getId())
                .orElse(null);

        return UserPrincipal.create(user, businessId);
    }
}

