package ru.defolt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final URI postUri = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final long interval;
    private long lastRequest;
    private final int requestLimit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.interval = timeUnit.toMillis(1);
        this.lastRequest = System.currentTimeMillis();
        this.requestLimit = requestLimit;
    }

    // Я не совсем понял для чего нужна подпись, и решил что для авторизации на сервере
    public void createDocument(Document document, String signature) {
        try {
            lock.lock();

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastRequest < interval && requestCount.get() >= requestLimit) {
                Thread.sleep(interval - (currentTime - lastRequest));
                requestCount.set(0);
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

            requestCount.incrementAndGet();
            lastRequest = System.currentTimeMillis();
        } catch (InterruptedException | IOException ignore) {

        } finally {
            lock.unlock();
        }
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
@Builder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
class Document {
    @Builder.Default
    Description description = new Description(null);
    @SerializedName(value = "doc_id")
    String docId;
    @SerializedName(value = "doc_status")
    String docStatus;
    @Builder.Default
    @SerializedName(value = "doc_type")
    String docType = "LP_INTRODUCE_GOODS";
    @Builder.Default
    boolean importRequest = true;
    @SerializedName(value = "owner_inn")
    String ownerInn;
    @SerializedName(value = "participant_inn")
    String participantInn;
    @SerializedName(value = "producer_inn")
    String producerInn;
    @Builder.Default
    @SerializedName(value = "production_date")
    LocalDate productionDate = LocalDate.of(2020, 1, 23);
    @SerializedName(value = "production_type")
    String productionType;
    @Builder.Default
    List<Product> products = new ArrayList<>();
    @Builder.Default
    @SerializedName(value = "reg_date")
    LocalDate regDate = LocalDate.of(2020, 1, 23);
    @SerializedName(value = "reg_number")
    String regNumber;
}

// Также вместо builder можно использовать record
record Product(String certificate_document,
               LocalDate certificate_document_date,
               String certificate_document_number,
               String owner_inn,
               String producer_inn,
               LocalDate production_date,
               String tnved_code,
               String uit_code,
               String uitu_code) {

}

record Description(String participantInn) {
}
