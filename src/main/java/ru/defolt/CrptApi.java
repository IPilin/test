package ru.defolt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final URI postUri = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");
    private final ReentrantLock lock = new ReentrantLock();
    private final TimeUnit timeUnit;
    private final long limit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.limit = requestLimit;
    }

    public static void main(String[] args) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new DateAdapter())
                .serializeNulls()
                .create();
        System.out.println(gson.toJson(Document.builder().build()));
    }

    // Я не совсем понял для чего нужна подпись, и решил что для авторизации на сервере
    public void createDocument(Document document, String signature) {
        try {
            if (!lock.tryLock(limit, timeUnit)) {
                return;
            }
            var httpClient = HttpClient.newHttpClient();
            var gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDate.class, new DateAdapter())
                    .serializeNulls()
                    .create();
            var request = HttpRequest.newBuilder()
                    .uri(postUri)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(document)))
                    .header("Authorization", "Basic " +
                            Base64.getEncoder().encodeToString(signature.getBytes()))
                    .build();
            var responce = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // можем проверить ответ сервера или игнорировать
            lock.unlock();
        } catch (InterruptedException | IOException ignore) { }
    }
}

class DateAdapter extends TypeAdapter<LocalDate> {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    @Override
    public void write(JsonWriter jsonWriter, LocalDate localDate) throws IOException {
        if (localDate == null) {
            jsonWriter.nullValue();
            return;
        }
        jsonWriter.value(localDate.format(formatter));
    }

    @Override
    public LocalDate read(JsonReader jsonReader) throws IOException {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull();
            return null;
        }
        return LocalDate.parse(jsonReader.nextString(), formatter);
    }
}

// Можно реализовать разные способы создания документа
// Я решил что builder будет самым удобным способом
@Builder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
class Document {
    @Builder.Default
    Description description = new Description(null);
    String docId;
    String docStatus;
    @Builder.Default
    String docType = "LP_INTRODUCE_GOODS";
    @Builder.Default
    boolean importRequest = true;
    String ownerInn;
    String participantInn;
    String producerInn;
    @Builder.Default
    LocalDate productionDate = LocalDate.of(2020, 1, 23);
    String productionType;
    @Builder.Default
    List<Product> products = new ArrayList<>();
    @Builder.Default
    LocalDate regDate = LocalDate.of(2020, 1, 23);
    String regNumber;
}

@Builder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
class Product {
    String certificateDocument;
    @Builder.Default
    LocalDate certificateDocumentDate = LocalDate.of(2020, 1, 23);
    String certificateDocumentNumber;
    String ownerInn;
    String producerInn;
    @Builder.Default
    LocalDate productionDate = LocalDate.of(2020, 1, 23);
    String tnvedCode;
    String uitCode;
    String uituCode;
}

record Description(String participantInn) {
}
