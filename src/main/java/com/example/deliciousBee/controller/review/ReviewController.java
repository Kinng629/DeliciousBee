package com.example.deliciousBee.controller.review;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

import com.example.deliciousBee.model.board.Restaurant;
import com.example.deliciousBee.model.member.BeeMember;
import com.example.deliciousBee.model.file.AttachedFile;
import com.example.deliciousBee.model.review.Review;
import com.example.deliciousBee.model.review.ReviewConverter;
import com.example.deliciousBee.model.review.ReviewLikeForm;
import com.example.deliciousBee.model.review.ReviewUpdateForm;
import com.example.deliciousBee.model.review.ReviewWriteForm;
import com.example.deliciousBee.service.member.BeeMemberService;
import com.example.deliciousBee.service.restaurant.RestaurantService;
import com.example.deliciousBee.service.review.ReviewService;
import com.example.deliciousBee.util.FileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/review")
public class ReviewController {

	private String uploadPath = "C:\\upload\\";
	private final ReviewService reviewService;
	private final FileService fileService;
	private final BeeMemberService beeMemberService;
	private final RestaurantService restaurantService;

	@GetMapping("allreview/{restaurant_id}")
	public String allReview(@PathVariable("restaurant_id") Long restaurant_id, Model model,
							@SessionAttribute(name = "loginMember") BeeMember loginMember) {

		String MemberId = loginMember.getMember_id();
		List<Review> allReview = reviewService.getReviewsByRestaurantIdWithFiles(restaurant_id, MemberId);
		Restaurant restaurant = restaurantService.findRestaurant(restaurant_id);
		model.addAttribute("restaurant", restaurant);
		model.addAttribute("allReview", allReview);
		model.addAttribute("uploadPath", uploadPath);
		return "review/allreview";
	}

	@GetMapping("write/{restaurant_id}")
	public String writeReview(@PathVariable("restaurant_id") Long restaurant_id, Model model) {
		model.addAttribute("writeform", new ReviewWriteForm());
		model.addAttribute("restaurant_id", restaurant_id);
		return "review/write";
	}

	@PostMapping("write/{restaurant_id}")
	public String postWriteReview(@Validated @ModelAttribute("writeForm") ReviewWriteForm reviewWriteForm,
								  BindingResult result, @RequestParam(name = "file", required = false) MultipartFile[] files,
								  @SessionAttribute(name = "loginMember") BeeMember loginMember,
								  @PathVariable(name = "restaurant_id") Long restaurant_id) {

		if (result.hasErrors()) {
			return "redirect:/";
		}

		if (loginMember == null) {
			return "redirect:/login";
		}

		Review review = ReviewConverter.reviewWriteFormToReview(reviewWriteForm);
		review.setBeeMember(loginMember);
		review.setUserName(loginMember.getName());

		Restaurant restaurant = restaurantService.findRestaurant(restaurant_id);
		review.setRestaurant(restaurant);

		List<AttachedFile> attachedFiles = new ArrayList<>();
		if (files != null && files.length > 0) {
			for (MultipartFile file : files) {
				if (!file.isEmpty()) {
					AttachedFile attachedFile = fileService.saveFile(file);
					attachedFile.setReview(review);
					attachedFiles.add(attachedFile);
				}
			}
		}
		reviewService.saveReview(review, attachedFiles);
		return "redirect:/review/allreview/" + restaurant_id;
	}

	// 이미지 출력을 위한 매서드
	@GetMapping("/display")
	public ResponseEntity<Resource> display(@RequestParam("filename") String filename) {

		String folder = "";
		Resource resource = new FileSystemResource(uploadPath + folder + filename);
		if (!resource.exists()) {
			return new ResponseEntity<Resource>(HttpStatus.NOT_FOUND);
		}
		HttpHeaders header = new HttpHeaders();
		Path filePath = null;
		try {
			filePath = Paths.get(uploadPath + folder + filename);
			header.add("Content-type", Files.probeContentType(filePath));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ResponseEntity<Resource>(resource, header, HttpStatus.OK);
	}

	// 리뷰 좋아요 처리
	@PostMapping("/{reviewId}")
	@ResponseBody
	public ResponseEntity<Map<Object, Object>> likeReview(@RequestBody ReviewLikeForm reviewLikeForm) {
		Review review = ReviewConverter.reviewLikeFormToReview(reviewLikeForm);
		int likeCount = reviewService.likeReview(review.getId());

		Map<Object, Object> reponse = new HashMap<>();
		boolean success = true;
		reponse.put("success", success);
		reponse.put("likeCount", likeCount);
		return ResponseEntity.ok(reponse);
	}

	// 리뷰삭제
	@PostMapping("/delete/{reviewId}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> deleteReview(@PathVariable("reviewId") Long reviewId) {
		Map<String, Object> response = new HashMap<>();
		boolean success = reviewService.deleteReview(reviewId);
		response.put("success", success);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/update/{reviewId}")
	public String getUpdateReview(@SessionAttribute(name = "loginMember", required = false) BeeMember loginMember,
								  @PathVariable("reviewId") Long reviewId, Model model) {
		Review findReview = reviewService.findReview(reviewId);
		if (findReview == null || !findReview.getBeeMember().getMember_id().equals(loginMember.getMember_id())) {
			log.info("허용 되지 않는 접근 방식입니다");
			return "redirect:/review/allreview/";
		}

		ReviewUpdateForm reviewUpdateForm = ReviewConverter.reviewToReviewUpdateForm(findReview);
		model.addAttribute("reviewUpdateForm", reviewUpdateForm);

		List<AttachedFile> attachedFile = reviewService.findFilesByReviewId(findReview.getId());
		if (attachedFile != null) {
			model.addAttribute("file", attachedFile);
		}

		return "/review/update";
	}

	// 리뷰 수정
	@PostMapping("/update")
	public String postUpdateReview(@Validated @ModelAttribute ReviewUpdateForm reviewUpdateForm, BindingResult result,
								   @RequestParam(name = "file", required = false) MultipartFile[] file) {
		Review updateReview = ReviewConverter.reviewUpdateFormToReview(reviewUpdateForm);
		log.info("**** updateReview:{}", updateReview);
		reviewService.updateReview(updateReview, reviewUpdateForm.isFileRemoved(), file);
		return "redirect:/";
	}

	// 수정시 첨부파일 삭제
	@PostMapping("/update/remove/{attachedFileId}")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> deleteAttachedFile(@PathVariable("attachedFileId") Long attachedFileId){
		Map<String, Object> response = new HashMap<>();
		boolean success = reviewService.deleteAttachedFile(attachedFileId);
		response.put("success", success);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/allreview/{restaurant_id}/sort/")
	@ResponseBody
	public List<Review> getSortAllReview(@RequestParam(required = false, defaultValue = "modifiedAt", value = "sortBy") String sortBy
			,@PathVariable("restaurant_id") Long restaurant_id
			,@SessionAttribute(name = "loginMember") BeeMember loginMember){
		try {
			String memberId = loginMember.getMember_id();
			log.info("정상적으로 처리가 되었습니다.");
			return reviewService.sortReview(sortBy, restaurant_id, memberId);
		} catch (Exception e) {
			log.error("서버 처리 중 오류 발생", e);
			return Collections.emptyList();
		}

	}
}