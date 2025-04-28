package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

public class TelegramAudioBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = "7738320805:AAHMo7bE8WTUOfkl02ggEcBrKXlHQvTWV2M";

    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new TelegramAudioBot());
    }

    @Override
    public String getBotUsername() {
        return "YourBotUsername"; // Change this to your bot's username
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            if (update.getMessage().hasAudio())
                processAudio(update);
            else if (update.getMessage().hasDocument())
                processDocAudio(update);
        }
    }

    private void processAudio(Update update) {
        long chatId = update.getMessage().getChatId();
        Audio audioFile = update.getMessage().getAudio();
        try {
            String fileUrl = getFileUrl(audioFile.getFileId());
            File originalFile = downloadFile(fileUrl, audioFile.getFileName());
            File oggFile = convertToOgg(originalFile);

            sendVoiceMessage(chatId, oggFile);

            // Clean up
            originalFile.delete();
            oggFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processDocAudio(Update update) {
        long chatId = update.getMessage().getChatId();
        /*
         * Document class with getDocument() method was chosen
         * as Telegram interprets wav's as documents rather than audios.
         */
        Document audioFile = update.getMessage().getDocument();
        try {
            String fileUrl = getFileUrl(audioFile.getFileId());
            File originalFile = downloadFile(fileUrl, audioFile.getFileName());
            File oggFile = convertToOgg(originalFile);

            sendVoiceMessage(chatId, oggFile);

            // Clean up
            originalFile.delete();
            oggFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFileUrl(String fileId) throws TelegramApiException {
        GetFile getFileMethod = new GetFile();
        getFileMethod.setFileId(fileId);
        String filePath = execute(getFileMethod).getFilePath();
        return "https://api.telegram.org/file/bot" + BOT_TOKEN + "/" + filePath;
    }

    private File downloadFile(String fileUrl, String outputFileName) throws IOException, InterruptedException {
        File file = new File(outputFileName);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream in = response.body()) {
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        return file;
    }

    private File convertToOgg(File inputFile) throws IOException, InterruptedException {
        File outputFile = new File("output.ogg");
        String command = String.format("ffmpeg.exe -i %s -vn -map_metadata -1 -c:a libopus -b:a 64k %s",
                inputFile.getAbsolutePath(),
                outputFile.getAbsolutePath()
        );
        // The command that i chose to use clears all unnecessary metadata, 
        // converts audio into Opus VBR file with 64kbps bitrate.
        // format 2 : "ffmpeg.exe -i %s -c:a libopus -b:a 64k %s"
        // "ffmpeg.exe -i %s -vn -map_metadata -1 -c:a libopus -b:a 64k %s"
        Process process = Runtime.getRuntime().exec(command);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {  // Timeout after 30 seconds
            process.destroy();
            throw new IOException("ffmpeg conversion timed out");
        }
        return outputFile;
    }

    private void sendVoiceMessage(long chatId, File voiceFile) throws TelegramApiException {
        SendVoice sendVoice = SendVoice.builder()
                .chatId(chatId)
                .voice(new org.telegram.telegrambots.meta.api.objects.InputFile(voiceFile))
                .build();
        execute(sendVoice);
    }
}
