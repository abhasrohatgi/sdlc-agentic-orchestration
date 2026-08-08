package com.urlshortner.service.impl;

import com.urlshortner.domain.dto.AnalyticsResponse;
import com.urlshortner.domain.entity.UrlAnalytics;
import com.urlshortner.domain.entity.UrlMapping;
import com.urlshortner.exception.UrlNotFoundException;
import com.urlshortner.repository.UrlAnalyticsRepository;
import com.urlshortner.repository.UrlMappingRepository;
import com.urlshortner.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final UrlAnalyticsRepository analyticsRepository;
    private final UrlMappingRepository urlMappingRepository;

    @Async
    @Override
    @Transactional
    public void recordClick(String shortCode, String referrer, String userAgent, String ipAddress) {
        try {
            UrlAnalytics analytics = UrlAnalytics.builder()
                    .shortCode(shortCode)
                    .clickTime(LocalDateTime.now())
                    .referrer(referrer != null ? referrer : "direct")
                    .userAgent(userAgent)
                    .ipAddress(ipAddress)
                    .build();

            analyticsRepository.save(analytics);
            urlMappingRepository.incrementClickCount(shortCode);
        } catch (Exception e) {
            log.error("Failed to record analytics click for shortCode: {}", shortCode, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        List<UrlAnalytics> clicks = analyticsRepository.findByShortCode(shortCode);

        long totalClicks = mapping.getClickCount() != null ? mapping.getClickCount() : clicks.size();

        Map<String, Long> referrerCounts = clicks.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getReferrer() != null ? a.getReferrer() : "direct",
                        Collectors.counting()
                ));

        List<AnalyticsResponse.ReferrerStat> referrersList = referrerCounts.entrySet().stream()
                .map(entry -> new AnalyticsResponse.ReferrerStat(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        LocalDateTime lastClickedAt = clicks.stream()
                .map(UrlAnalytics::getClickTime)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .totalClicks(totalClicks)
                .lastClickedAt(lastClickedAt)
                .referrers(referrersList)
                .build();
    }

    private String parseOperatingSystem(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("android")) {
            return "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("cpu os")) {
            return "iOS";
        } else if (ua.contains("windows")) {
            return "Windows";
        } else if (ua.contains("mac os") || ua.contains("macintosh")) {
            return "macOS";
        } else if (ua.contains("linux")) {
            return "Linux";
        } else {
            return "Other";
        }
    }
}