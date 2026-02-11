package com.example.notificationservice.service;

import com.example.notificationservice.domain.ChannelType;
import com.example.notificationservice.domain.Template;
import com.example.notificationservice.repository.TemplateRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves templates with ${varName} placeholders. Caches template definitions.
 */
@Service
public class TemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final TemplateRepository templateRepository;

    public TemplateService(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Cacheable(cacheNames = "templates", key = "#templateId + '-' + #channelType.name() + '-' + #locale")
    public ResolvedTemplate resolve(String templateId, ChannelType channelType, String locale, Map<String, String> variables) {
        String effectiveLocale = locale != null && !locale.isBlank() ? locale : "en";
        Template template = templateRepository.findByIdAndLocaleAndActiveTrue(templateId, effectiveLocale)
            .or(() -> templateRepository.findByIdAndActiveTrue(templateId))
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (template.getChannelType() != channelType) {
            throw new IllegalArgumentException("Template " + templateId + " is not for channel " + channelType);
        }

        String subject = template.getSubjectTemplate() != null
            ? substitute(template.getSubjectTemplate(), variables)
            : null;
        String body = substitute(template.getBodyTemplate(), variables);
        return new ResolvedTemplate(subject, body);
    }

    public static String substitute(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) return template;
        if (variables == null || variables.isEmpty()) return template;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String value = variables.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public record ResolvedTemplate(String subject, String body) {}
}
