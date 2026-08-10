package com.platform.tagquery.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.internet.MimeMessage;
import java.util.Map;

/**
 * 邮件通知服务（Day 8）。
 *
 * 📖 @Async("logExecutor")：发邮件走 SMTP 是秒级网络 IO，同步发会卡住拉取/集成线程。
 *    复用日志线程池即可（都是"通知类"慢任务）。
 *
 * 🔐 安全：模板变量全部走 Thymeleaf 转义（th:text 默认 HTML 转义），
 *    S3 路径/失败原因里就算带 <script> 也只会当文本显示，防邮件客户端 XSS。
 */
@Slf4j
@Service
public class NotificationService {

    @Value("${notify.receivers:}")
    private String notifyReceivers;

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public NotificationService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Async("logExecutor")
    public void sendTaskCompleteNotification(Map<String, Object> vars) {
        sendMail("task-complete", "【联合计算中心】实时标签服务数据同步完成通知", vars);
    }

    @Async("logExecutor")
    public void sendPullFailureNotification(Map<String, Object> vars) {
        sendMail("pull-failure", "【联合计算中心】实时标签服务拉取失败告警", vars);
    }

    @Async("logExecutor")
    public void sendIntegrationFailureNotification(Map<String, Object> vars) {
        sendMail("integration-failure", "【联合计算中心】实时标签服务数据集成失败告警", vars);
    }

    private void sendMail(String template, String subject, Map<String, Object> vars) {
        if (notifyReceivers == null || notifyReceivers.isBlank()) {
            log.warn("未配置 notify.receivers，邮件不发送 subject={}", subject);
            return;
        }
        try {
            Context context = new Context();
            context.setVariables(vars);
            String html = templateEngine.process(template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setSubject(subject);
            helper.setText(html, true);
            for (String receiver : notifyReceivers.split(",")) {
                if (receiver != null && !receiver.isBlank()) {
                    helper.setTo(receiver.trim());
                    mailSender.send(message);
                }
            }
        } catch (Exception e) {
            // 📖 发件失败绝不能影响主流程，吞掉但留痕
            log.error("邮件发送失败 subject={}", subject, e);
        }
    }
}
