package com.islandride.reviews.repository;

import com.islandride.reviews.domain.Review;
import com.islandride.reviews.domain.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByPublicId(String publicId);

    Optional<Review> findByBookingId(Long bookingId);

    List<Review> findByVehicleIdAndStatus(Long vehicleId, ReviewStatus status);

    List<Review> findByBusinessIdAndStatus(Long businessId, ReviewStatus status);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.vehicle.id = :vehicleId AND r.status = :status")
    Double findAverageRatingByVehicleId(@Param("vehicleId") Long vehicleId, @Param("status") ReviewStatus status);
}

