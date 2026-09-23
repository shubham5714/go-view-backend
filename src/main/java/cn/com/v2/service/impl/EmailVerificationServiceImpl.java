package cn.com.v2.service.impl;

import cn.com.v2.mapper.EmailVerificationMapper;
import cn.com.v2.model.EmailVerification;
import cn.com.v2.service.IEmailVerificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
public class EmailVerificationServiceImpl extends ServiceImpl<EmailVerificationMapper, EmailVerification>
        implements IEmailVerificationService {

    @Value("${sendgrid.api-key:}")
    private String sendgridApiKey;

    @Value("${sendgrid.from-email:no-reply@example.com}")
    private String fromEmail;

    @Value("${sendgrid.from-name:Vscreen}")
    private String fromName;

    private static final String PURPOSE_SIGNUP = "SIGNUP";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void sendSignupCode(String email) {
        if (sendgridApiKey == null || sendgridApiKey.trim().isEmpty()) {
            throw new RuntimeException("Email service is not configured");
        }

        String code = generateCode();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(10);

        EmailVerification ev = new EmailVerification();
        ev.setEmail(email);
        ev.setCode(code);
        ev.setPurpose(PURPOSE_SIGNUP);
        ev.setExpiresAt(expiresAt.format(FORMATTER));
        ev.setConsumed(0);
        ev.setCreatedTime(now.format(FORMATTER));
        this.save(ev);

        sendEmail(email, buildSignupEmailBody(code));
    }

    @Override
    public boolean verifySignupCode(String email, String code) {
        LambdaQueryWrapper<EmailVerification> wrapper = new LambdaQueryWrapper<EmailVerification>()
                .eq(EmailVerification::getEmail, email)
                .eq(EmailVerification::getPurpose, PURPOSE_SIGNUP)
                .eq(EmailVerification::getConsumed, 0)
                .orderByDesc(EmailVerification::getCreatedTime)
                .last("LIMIT 1");
        EmailVerification latest = this.getOne(wrapper);
        if (latest == null) {
            return false;
        }
        // Expiry check
        LocalDateTime expiresAt = LocalDateTime.parse(latest.getExpiresAt(), FORMATTER);
        if (LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        if (!latest.getCode().equals(code)) {
            return false;
        }
        latest.setConsumed(1);
        this.updateById(latest);
        return true;
    }

    private String generateCode() {
        int value = new Random().nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private String buildSignupEmailBody(String code) {
        return "<!DOCTYPE html>" +
                "<html lang=\"en\">" +
                "<head><meta charset=\"UTF-8\"><title>Vscreen verification code</title></head>" +
                "<body style=\"font-family: Arial, sans-serif; font-size: 14px; color: #222;\">" +
                "<p>Hi,</p>" +
                "<p>Your <strong>Vscreen</strong> verification code is:</p>" +
                "<p style=\"font-size: 20px; font-weight: bold; letter-spacing: 3px;\">" + code + "</p>" +
                "<p>This code will expire in <strong>10 minutes</strong>.</p>" +
                "<p>If you did not request this code, you can safely ignore this email.</p>" +
                "<p>Thanks,<br/>Vscreen Team</p>" +
                "</body></html>";
    }

    private void sendEmail(String toEmail, String body) {
        Email from = new Email(fromEmail, fromName);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", body);
        Mail mail = new Mail(from, "Vscreen verification code", to, content);

        SendGrid sg = new SendGrid(sendgridApiKey);
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            sg.api(request);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}

