package com.example.projectammu

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object SmtpEmailSender {

    fun isConfigured(): Boolean {
        return BuildConfig.LOST_PHONE_EMAIL_TO.isNotBlank() &&
            BuildConfig.LOST_PHONE_EMAIL_FROM.isNotBlank() &&
            BuildConfig.LOST_PHONE_SMTP_USERNAME.isNotBlank() &&
            BuildConfig.LOST_PHONE_SMTP_PASSWORD.isNotBlank()
    }

    fun sendLostPhoneLocation(subject: String, body: String) {
        val properties = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", BuildConfig.LOST_PHONE_SMTP_HOST)
            put("mail.smtp.port", BuildConfig.LOST_PHONE_SMTP_PORT)
        }

        val session = Session.getInstance(
            properties,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(
                        BuildConfig.LOST_PHONE_SMTP_USERNAME,
                        BuildConfig.LOST_PHONE_SMTP_PASSWORD
                    )
                }
            }
        )

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(BuildConfig.LOST_PHONE_EMAIL_FROM))
            setRecipients(
                Message.RecipientType.TO,
                InternetAddress.parse(BuildConfig.LOST_PHONE_EMAIL_TO)
            )
            setSubject(subject)
            setText(body)
        }

        Transport.send(message)
    }
}
