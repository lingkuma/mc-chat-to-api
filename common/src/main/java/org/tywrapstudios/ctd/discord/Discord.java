package org.tywrapstudios.ctd.discord;

import gs.mclo.api.Log;
import gs.mclo.api.response.UploadLogResponse;
import org.tywrapstudios.ctd.CTDCommon;
import org.tywrapstudios.ctd.discord.messagetypes.Embed;
import org.tywrapstudios.ctd.discord.messagetypes.PlainMessage;
import org.tywrapstudios.ctd.discord.resources.Footer;
import org.tywrapstudios.ctd.discord.webhook.WebhookClient;
import org.tywrapstudios.ctd.discord.webhook.WebhookConnector;
import org.tywrapstudios.ctd.platform.CTDServices;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static org.tywrapstudios.ctd.CTDCommon.MCL;

public class Discord {
    public static void sendLiteralToDiscord(String message, boolean embedMode, String webhookUrl) {
        if (!embedMode) {
            PlainMessage literalMessage = new PlainMessage()
                    .setContent(message);
            new WebhookConnector()
                    .setChannelUrl(webhookUrl)
                    .setMessage(literalMessage)
                    .setListener(new WebhookClient.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            logSuccess("CTD-Literal","CTD-Literal",message);
                        }

                        @Override
                        public void onFailure(int statusCode, String errorMessage) {
                            logFailure(message, statusCode, errorMessage, "CTD-Literal", "CTD-Literal");
                        }
                    })
                    .exec();
        } else {
            Footer footer = new Footer(message,"https://media.discordapp.net/attachments/1249069998148812930/1293350885837242388/minecraft_logo.png?ex=67070e60&is=6705bce0&hm=33b6d9a9ed182dc00bf080fbfa344a9f27781fde92d9cc9f4d4cfcc54ef40d47&=&format=webp&quality=lossless&width=889&height=889");
            Embed embed = new Embed()
                    .setColor(CTDCommon.CONFIG_MANAGER.getConfig().discord_config.embed_color_rgb_int)
                    .setFooter(footer);
            PlainMessage embedMessage = new PlainMessage()
                    .setContent("");
            new WebhookConnector()
                    .setChannelUrl(webhookUrl)
                    .setEmbeds(new Embed[]{embed})
                    .setMessage(embedMessage)
                    .setListener(new WebhookClient.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            logSuccess("CTD-Literal","CTD-Literal",message);
                        }

                        @Override
                        public void onFailure(int statusCode, String errorMessage) {
                            logFailure(message, statusCode, errorMessage, "CTD-Literal", "CTD-Literal");
                        }
                    })
                    .exec();
        }
    }

    public static void sendChatMessageToDiscord(String chatMessage, String playerName, String webhookUrl, String UUID) {
        PlainMessage message = new PlainMessage()
                .setContent("**"+playerName+":** "+chatMessage);
        new WebhookConnector()
                .setChannelUrl(webhookUrl)
                .setMessage(message)
                .setListener(new WebhookClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        logSuccess(playerName, UUID, chatMessage);
                    }
                    @Override
                    public void onFailure(int statusCode, String errorMessage) {
                        logFailure(chatMessage, statusCode, errorMessage, playerName, UUID);
                    }
                })
                .exec();
        sendToCustomUrl(chatMessage, playerName, UUID);
    }

    public static void sendEmbedToDiscord(String chatMessage, String playerName, String webhookUrl, String UUID, int embedColor) {
        // For embeds, we send the raw chat message to the custom URL, not the embed content.
        sendToCustomUrl(chatMessage, playerName, UUID);
        Footer footer = new Footer(playerName+": "+chatMessage,"https://mc-heads.net/avatar/"+UUID+"/90");
        Embed embed = new Embed()
                .setColor(embedColor)
                .setFooter(footer);
        PlainMessage message = new PlainMessage()
                .setContent("");
        new WebhookConnector()
                .setChannelUrl(webhookUrl)
                .setEmbeds(new Embed[]{embed})
                .setMessage(message)
                .setListener(new WebhookClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        logSuccess(playerName, UUID, chatMessage);
                    }

                    @Override
                    public void onFailure(int statusCode, String errorMessage) {
                        logFailure(chatMessage, statusCode, errorMessage, playerName, UUID);
                    }
                })
                .exec();
    }

    public static void sendCrashEmbed(String cause, int embedColor, String webhookUrl, Path log) {
        MCL.setMinecraftVersion(CTDServices.PLATFORM.getModVersion("minecraft"));
        UploadLogResponse response = null;
        boolean canSend = true;

        try {
            Log stack = log != null ? new Log(log) : new Log(Arrays.toString(new Exception("CTD Debug").getStackTrace()));
            response = MCL.uploadLog(stack).get().setClient(MCL);
        } catch (ExecutionException | InterruptedException | IOException e) {
            sendLiteralToDiscord("Minecraft experienced an exception, but CTD could not add an accompanying crash message. Please check your logs.", CTDCommon.CONFIG_MANAGER.getConfig().discord_config.embed_mode, webhookUrl);
            canSend = false;
            e.printStackTrace();
        }

        if (!canSend || response == null) return;

        String description = String.format("""
                **Minecraft crashed with the following given cause:**
                [`%s`]
                **Stacktrace:**
                [[`%s`](<%s>)]""", cause, response.getUrl().replace("https://mclo.gs/", ""), response.getUrl());
        Embed embed = new Embed()
                .setColor(embedColor)
                .setTitle("MINECRAFT EXPERIENCED AN EXCEPTION!")
                .setDescription(description);
        PlainMessage message = new PlainMessage()
                .setContent("");
        new WebhookConnector()
                .setChannelUrl(webhookUrl)
                .setEmbeds(new Embed[]{embed})
                .setMessage(message)
                .setListener(new WebhookClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        logSuccess("CTD", "CTD-Internals", "Sent a Crash notice to the webhook(s).");
                    }

                    @Override
                    public void onFailure(int statusCode, String errorMessage) {
                        logFailure(cause, statusCode, errorMessage, "CTD", "CTD-Internals");
                    }
                })
                .exec();
    }

    public static void logSuccess(String playerName, String UUID, String chatMessage) {
        String log = String.format("[%s[%s]: %s]", playerName, UUID, chatMessage);
        CTDCommon.LOGGING.debug(log);
    }

    public static void logFailure(String chatMessage, int statusCode, String errorMessage, String playerName, String UUID) {
        CTDCommon.LOGGING.warn(String.format("Message \"%s\" by %s[%s] failed to send. ", chatMessage, playerName, UUID));
        CTDCommon.LOGGING.warn(String.format("Code: %s Error: %s", statusCode, errorMessage));
    }

    private static void sendToCustomUrl(String chatMessage, String playerName, String uuid) {
        if (!CTDCommon.CONFIG_MANAGER.getConfig().chat_post_config.enable_chat_posting ||
                CTDCommon.CONFIG_MANAGER.getConfig().chat_post_config.chat_post_url == null ||
                CTDCommon.CONFIG_MANAGER.getConfig().chat_post_config.chat_post_url.isEmpty()) {
            return;
        }

        String targetUrlString = CTDCommon.CONFIG_MANAGER.getConfig().chat_post_config.chat_post_url;
        String postFormat = CTDCommon.CONFIG_MANAGER.getConfig().chat_post_config.chat_post_format;

        try {
            // Replace placeholders in the post format
            String requestBody = postFormat
                    .replace("{text}", chatMessage)
                    .replace("{playerName}", playerName != null ? playerName : "UnknownPlayer")
                    .replace("{uuid}", uuid != null ? uuid : "UnknownUUID");

            // If the target URL does not contain "?", we assume it's a POST request
            // or a GET request where parameters will be added if it's the default "{text}" format.
            // For simplicity with the original request "http://127.0.0.1:2333/api/translate?text=聊天内容"
            // we will primarily support GET for simple text, and POST for formatted text.

            HttpURLConnection connection = null;
            URL url;

            // Check if the user specifically wants to append 'text' as a query param for GET
            // This is a common simple use case.
            if (!targetUrlString.contains("?") && postFormat.equals("{\"text\": \"{text}\"}")) { // Default GET-like scenario
                 url = new URL(targetUrlString + "?text=" + URLEncoder.encode(chatMessage, StandardCharsets.UTF_8.name()));
                 connection = (HttpURLConnection) url.openConnection();
                 connection.setRequestMethod("GET");
            } else if (targetUrlString.toLowerCase().startsWith("http://") || targetUrlString.toLowerCase().startsWith("https://")) {
                // Assumed POST for more complex formats or if URL already has query params
                url = new URL(targetUrlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); // Assume JSON for POST
                if (postFormat.equals("{text}")) { // Plain text POST
                    connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
                }
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            } else {
                CTDCommon.LOGGING.warn("[Custom URL] Invalid URL specified: " + targetUrlString);
                return;
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                CTDCommon.LOGGING.debug(String.format("[Custom URL] Successfully sent message to %s. Response code: %d", targetUrlString, responseCode));
            } else {
                CTDCommon.LOGGING.warn(String.format("[Custom URL] Failed to send message to %s. Response code: %d, Message: %s", targetUrlString, responseCode, connection.getResponseMessage()));
            }
            connection.disconnect();

        } catch (Exception e) {
            CTDCommon.LOGGING.error("[Custom URL] Error sending message: " + e.getMessage());
            // e.printStackTrace(); // Optionally print stack trace for more detailed debugging
        }
    }
}
