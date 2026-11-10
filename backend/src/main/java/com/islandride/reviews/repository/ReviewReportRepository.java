package com.islandride.reviews.repository;

import com.islandride.reviews.domain.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {

    List<ReviewReport> findByReviewId(Long reviewId);

    List<ReviewReport> findByStatus(String status);
}

