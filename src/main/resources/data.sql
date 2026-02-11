-- Sample templates for development (H2)
INSERT INTO templates (id, name, channel_type, locale, subject_template, body_template, active)
VALUES ('welcome-email', 'Welcome Email', 'EMAIL', 'en', 'Welcome, ${userName}!', 'Hi ${userName}, please sign in here: ${loginUrl}', true);

INSERT INTO templates (id, name, channel_type, locale, subject_template, body_template, active)
VALUES ('welcome-sms', 'Welcome SMS', 'SMS', 'en', NULL, 'Hi ${userName}, your code is ${code}.', true);

INSERT INTO templates (id, name, channel_type, locale, subject_template, body_template, active)
VALUES ('welcome-push', 'Welcome Push', 'PUSH', 'en', 'Hello ${userName}', 'Welcome! Sign in: ${loginUrl}', true);

INSERT INTO templates (id, name, channel_type, locale, subject_template, body_template, active)
VALUES ('welcome-whatsapp', 'Welcome WhatsApp', 'WHATSAPP', 'en', NULL, 'Hi ${userName}, welcome. Login: ${loginUrl}', true);
