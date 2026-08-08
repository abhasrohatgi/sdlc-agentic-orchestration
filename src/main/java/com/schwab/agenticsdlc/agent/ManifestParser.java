package com.schwab.agenticsdlc.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Utility class for parsing the Phase 1 JSON manifest into Java DTOs.
 * Supports parsing from both a raw JSON string (LLM response) and a file path.
 */
public class ManifestParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Parses the Phase 1 manifest from a raw JSON string (direct LLM response).
     *
     * @param jsonContent Raw JSON string returned by the LLM.
     * @return Parsed ProjectManifest object.
     * @throws Exception If JSON is malformed or missing required fields.
     */
    public static ProjectManifest parseFromString(String jsonContent) throws Exception {
        if (jsonContent == null || jsonContent.isBlank()) {
            throw new IllegalArgumentException("Manifest JSON content cannot be null or blank.");
        }
        // Strip optional markdown code fences if LLM accidentally wraps JSON
        String cleaned = jsonContent.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }
        try {
            return OBJECT_MAPPER.readValue(cleaned, ProjectManifest.class);
        } catch (Exception primaryEx) {
            org.slf4j.LoggerFactory.getLogger(ManifestParser.class)
                    .warn("[ManifestParser WARNING]: Manifest JSON parsing failed ({}); executing auto-repair...", primaryEx.getMessage());

            // Tier 1: Auto-repair truncated JSON by truncating to last valid file entry and appending closing brackets
            try {
                String repaired = repairTruncatedManifestJson(cleaned);
                ProjectManifest manifest = OBJECT_MAPPER.readValue(repaired, ProjectManifest.class);
                if (manifest != null && manifest.getFiles() != null && !manifest.getFiles().isEmpty()) {
                    org.slf4j.LoggerFactory.getLogger(ManifestParser.class)
                            .info("[ManifestParser SUCCESS]: Auto-repaired truncated JSON manifest — recovered {} files.", manifest.getFiles().size());
                    return manifest;
                }
            } catch (Exception secondaryEx) {
                // Ignore and fall through to Tier 2 regex extractor
            }

            // Tier 2: Resilient Regex Extraction of all valid FileNode blocks
            ProjectManifest fallback = extractPartialManifest(cleaned);
            if (fallback != null && fallback.getFiles() != null && !fallback.getFiles().isEmpty()) {
                org.slf4j.LoggerFactory.getLogger(ManifestParser.class)
                        .info("[ManifestParser SUCCESS]: Resilient regex extractor recovered {} files from truncated manifest.", fallback.getFiles().size());
                return fallback;
            }

            throw primaryEx;
        }
    }

    private static String repairTruncatedManifestJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        // Find last complete FileNode block ending with '}'
        int lastBrace = json.lastIndexOf('}');
        if (lastBrace > 0) {
            String sub = json.substring(0, lastBrace + 1).trim();
            if (sub.endsWith(",")) {
                sub = sub.substring(0, sub.length() - 1).trim();
            }
            if (!sub.endsWith("]")) {
                sub += "\n  ]";
            }
            if (!sub.endsWith("}")) {
                sub += "\n}";
            }
            return sub;
        }
        return json;
    }

    private static ProjectManifest extractPartialManifest(String json) {
        ProjectManifest manifest = new ProjectManifest();
        java.util.List<FileNode> nodes = new java.util.ArrayList<>();

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\{\\s*\"path\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"fileType\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"purpose\"\\s*:\\s*\"([^\"]*)\"\\s*\\}",
                java.util.regex.Pattern.DOTALL);

        java.util.regex.Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            FileNode node = new FileNode();
            node.setPath(matcher.group(1));
            node.setFileType(matcher.group(2));
            node.setPurpose(matcher.group(3));
            nodes.add(node);
        }

        if (!nodes.isEmpty()) {
            manifest.setFiles(nodes);
            java.util.regex.Matcher projMatcher = java.util.regex.Pattern.compile("\"projectId\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (projMatcher.find()) {
                manifest.setProjectId(projMatcher.group(1));
            }
            return manifest;
        }
        return null;
    }

    /**
     * Parses the Phase 1 manifest from a file path on disk.
     *
     * @param jsonFilePath Absolute path to the JSON manifest file.
     * @return Parsed ProjectManifest object.
     * @throws Exception If file is unreadable or JSON is malformed.
     */
    public static ProjectManifest parseManifest(String jsonFilePath) throws Exception {
        return OBJECT_MAPPER.readValue(new java.io.File(jsonFilePath), ProjectManifest.class);
    }

    // ─── DTOs ────────────────────────────────────────────────────────────────

    /**
     * DTO representing the full Phase 1 file manifest.
     */
    public static class ProjectManifest {

        @JsonProperty("projectId")
        private String projectId;

        @JsonProperty("files")
        private List<FileNode> files;

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }

        public List<FileNode> getFiles() { return files; }
        public void setFiles(List<FileNode> files) { this.files = files; }
    }

    /**
     * DTO representing a single file entry in the Phase 1 manifest.
     */
    public static class FileNode {

        @JsonProperty("path")
        private String path;

        @JsonProperty("fileType")
        private String fileType;

        @JsonProperty("purpose")
        private String purpose;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }

        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
    }
}
