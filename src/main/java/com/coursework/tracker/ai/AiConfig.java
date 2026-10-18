package com.coursework.tracker.ai;

/** DeepSeek（OpenAI 兼容）配置。 */
public record AiConfig(boolean enabled, String apiKey, String baseUrl, String model) {

    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-flash";

    public static AiConfig disabled() {
        return new AiConfig(false, "", DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }

    public String endpoint() {
        String base = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    public String maskedKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
