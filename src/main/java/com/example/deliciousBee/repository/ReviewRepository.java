package com.example.deliciousBee.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.deliciousBee.model.review.Review;

public interface ReviewRepository extends JpaRepository<Review, Long>{

	// 레스토랑 ID를 통해서 review 가져오기
	List<Review> findByRestaurantId(Long restaurantId);

	// 추천순으로 정렬
	List<Review> findAllByOrderByRatingDesc();

	// 등록순으로 정렬
	List<Review> findAllByOrderByCreateDateDesc();


}
