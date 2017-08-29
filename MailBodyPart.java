package com.visionet.letsdesk.app.common.mail;

import org.apache.commons.lang3.StringUtils;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by yangp on 2017/8/29.
 */
public class MailBodyPart extends MimeBodyPart {
    private BodyPart bodyPart;

    public MailBodyPart(BodyPart bodyPart) {
        this.bodyPart = bodyPart;
    }

    @Override
    public String getContent() throws IOException, MessagingException {
        String content = (String) bodyPart.getContent();
        String[] s = bodyPart.getHeader("Content-Type");
        if (s.length > 0 && s[0].contains("gb2312")) {
            InputStream in = bodyPart.getInputStream();
            StringBuffer sb = new StringBuffer();
            String value = null;
            try {
                InputStreamReader isr = new InputStreamReader(in, "gbk");
                BufferedReader br = new BufferedReader(isr);
                while (StringUtils.isNotEmpty(value = br.readLine())) {
                    sb.append(value);
                }
                isr.close();
                content = sb.toString();
            } catch (Exception e) {
                e.printStackTrace();
                in.close();
            }
        }
        return content;
    }

}
