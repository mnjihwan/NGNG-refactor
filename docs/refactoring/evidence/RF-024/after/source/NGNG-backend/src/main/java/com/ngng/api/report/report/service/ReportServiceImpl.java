package com.ngng.api.report.report.service;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.report.report.dto.*;
import com.ngng.api.report.report.entity.Report;
import com.ngng.api.report.report.entity.ReportImage;
import com.ngng.api.report.report.entity.ReportType;
import com.ngng.api.report.report.repository.ReportImageRepository;
import com.ngng.api.report.report.repository.ReportRepository;
import com.ngng.api.report.report.repository.ReportTypeRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "report-log")
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ReportTypeRepository reportTypeRepository;
    private final ReportImageRepository reportImageRepository;
    private final AuthService authService;


    @Override
    public Page<ReadReportListResponseDTO> findAll(Integer page, Boolean unprocessedOnly) {
        List<Sort.Order> sorts = new ArrayList<>();
        sorts.add(Sort.Order.desc("reportId"));
        Pageable pageable = PageRequest.of(page, 5, Sort.by(sorts));

        // 미처리 신고 분기
        Page<Report> reports;
        if(unprocessedOnly) {
            reports = reportRepository.findByIsReport(null, pageable);
        } else {
            reports = reportRepository.findAll(pageable);
        }

        return reports.map(report -> ReadReportListResponseDTO.builder()
                        .reportId(report.getReportId())
                        .reportContents(report.getReportContents())
                        .reportType(report.getReportType())
                        .reporter(report.getReporter())
                        .user(report.getUser())
                        .isReport(report.getIsReport())
                        .createdAt(report.getCreatedAt())
                        .productId(report.getProductId())
                        .privateChatId(report.getPrivateChatId())
                        .visible(report.getVisible())
                        .build());
    }

    @Override
    public ReadReportResponseDTO findById(Long reportId) {
        Optional<Report> reportOptional = reportRepository.findById(reportId);

        Report report = reportOptional.orElse(null);

        return ReadReportResponseDTO.builder()
                .reportId(report.getReportId())
                .reportContents(report.getReportContents())
                .reportType(report.getReportType())
                .reporter(report.getReporter())
                .user(report.getUser())
                .isReport(report.getIsReport())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .productId(report.getProductId())
                .privateChatId(report.getPrivateChatId())
                .reportImages(report.getReportImages().stream()
                        .map(ReportImage::getReportImg)
                        .collect(Collectors.toList()))
                .build();

    }

    @Override
    public CreateReportResponseDTO save(CreateReportRequestDTO createReportRequestDTO) {
        User caller = readCreationCaller();
        if (createReportRequestDTO.getReporterId() != null
                && !caller.getUserId().equals(createReportRequestDTO.getReporterId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사용자의 이름으로 신고할 수 없습니다.");
        }
        if (createReportRequestDTO.getIsReport() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고 처리 상태를 지정할 수 없습니다.");
        }
        Optional<ReportType> reportType = reportTypeRepository.findById(createReportRequestDTO.getReportTypeId());
        ReportType responseReportType = reportType.orElseThrow(); // TODO 여기 예외처리하기

        Report report = Report.builder()
                .reportContents(createReportRequestDTO.getReportContents())
                .reportType(ReportType.builder()
                        .reportTypeId(createReportRequestDTO.getReportTypeId())
                        .reportType(responseReportType.getReportType())
                        .build())
                .reporterId(caller.getUserId())
                .userId(createReportRequestDTO.getUserId())
                .isReport(null)
                .productId(createReportRequestDTO.getProductId())
                .privateChatId(createReportRequestDTO.getPrivateChatId())
                .build();

        Report responseReport = reportRepository.save(report);

        log.info("Success Create report id: {} reporter : {}  user : {} product : {}",responseReport.getReporterId(),responseReport.getReporterId(),responseReport.getUserId(),responseReport.getProductId());
        return CreateReportResponseDTO.builder()
                .reportId(responseReport.getReportId())
                .reportContents(responseReport.getReportContents())
                .reportType(responseReport.getReportType())
                .reporter(responseReport.getReporter())
                .user(responseReport.getUser())
                .isReport(responseReport.getIsReport())
                .createdAt(responseReport.getCreatedAt())
                .updatedAt(responseReport.getUpdatedAt())
                .productId(responseReport.getProductId())
                .privateChatId(responseReport.getPrivateChatId())
                .build();
    }

    private User readCreationCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)
                || principal.getUser() == null || principal.getUser().getEmail() == null
                || principal.getUser().getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        User caller = authService.readAuthUser();
        if (caller == null || caller.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        return caller;
    }


    @Override
    public ReadReportResponseDTO update(Long reportId, Boolean isReport) {

        Report report = reportRepository.findById(reportId).orElseThrow(() ->
                new EntityNotFoundException("report not found")
        );

        report.setIsReport(isReport);

        Report responseReport = reportRepository.save(report);

        log.info("Success update report id: {} isReport : {} ",responseReport.getReporterId(),isReport);
        return ReadReportResponseDTO.builder()
                .reportId(responseReport.getReportId())
                .reportContents(responseReport.getReportContents())
                .reportType(responseReport.getReportType())
                .reporter(responseReport.getReporter())
                .user(responseReport.getUser())
                .isReport(responseReport.getIsReport())
                .createdAt(responseReport.getCreatedAt())
                .updatedAt(responseReport.getUpdatedAt())
                .productId(responseReport.getProductId())
                .privateChatId(responseReport.getPrivateChatId())
                .reportImages(responseReport.getReportImages().stream()
                        .map(ReportImage::getReportImg)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public DeleteReportResponseDTO delete(Long reportId) {
        Report report = reportRepository.findById(reportId).orElseThrow(() ->
                new EntityNotFoundException("report not found")
        );

        report.setVisible(true);

        Report responseReport = reportRepository.save(report);

        log.info("Success Delete report id: {}",responseReport.getReporterId());
        return DeleteReportResponseDTO.builder()
                .reportId(responseReport.getReportId())
                .visible(responseReport.getVisible())
                .build();

    }


}
