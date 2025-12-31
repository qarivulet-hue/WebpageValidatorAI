package WebpageValidatorAIPKG;

import com.google.gson.*;
import io.github.bonigarcia.wdm.WebDriverManager;
import okhttp3.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SeleniumOpenAIGrammarChecker {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private static final Gson gson = new Gson();
    private static final int BATCH_SIZE = 3;
    private static final int MAX_RETRIES = 3;
    private static final int BASE_BATCH_DELAY_MS = 15000;

    public static void main(String[] args) throws Exception {
        WebDriver driver = null;
        String openAiKey = System.getenv("OPENAI_API_KEY");

        if (openAiKey == null || openAiKey.isEmpty()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set.");
            return;
        }

        String url = "https://www.forwardfocuslogistics.com/terms-of-use/";

        try {
            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-dev-shm-usage", "--disable-gpu", "--headless", "--remote-allow-origins=*");

            driver = new ChromeDriver(options);
            driver.get(url);
            System.out.println("Loading page: " + url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Scroll down to bottom to trigger lazy loading of content
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long lastHeight = (long) js.executeScript("return document.body.scrollHeight");

            while (true) {
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(2000);  // Wait 2 seconds for content to load
                long newHeight = (long) js.executeScript("return document.body.scrollHeight");
                if (newHeight == lastHeight) {
                    break;  // No more new content
                }
                lastHeight = newHeight;
            }

            // Allow some extra wait to ensure content is loaded
            Thread.sleep(2000);

            // Extract only visible <p> tags and remove duplicates
            Set<String> uniqueParagraphsSet = new LinkedHashSet<>();
            List<WebElement> pTags = driver.findElements(By.tagName("p"));
            System.out.println("Total <p> tags found: " + pTags.size());

            for (WebElement p : pTags) {
                if (!p.isDisplayed()) continue;
                String text = p.getText().trim();
                if (!text.isEmpty() && text.split("\\s+").length > 5) {  // Filter short text
                    uniqueParagraphsSet.add(text);
                }
            }

            List<String> paragraphs = new ArrayList<>(uniqueParagraphsSet);
            System.out.println("Total unique meaningful paragraphs extracted: " + paragraphs.size());

            if (paragraphs.isEmpty()) {
                System.out.println("No suitable content found.");
                return;
            }

            List<ParagraphAnalysis> results = new ArrayList<>();

            for (int i = 0; i < paragraphs.size(); i += BATCH_SIZE) {
                List<String> batch = paragraphs.subList(i, Math.min(i + BATCH_SIZE, paragraphs.size()));
                System.out.println("\nProcessing batch " + (i / BATCH_SIZE + 1) + "...");
                List<ParagraphAnalysis> batchResults = new ArrayList<>();
                long delayMs = BASE_BATCH_DELAY_MS;

                for (String paragraph : batch) {
                    int retryCount = 0;
                    String analysis = null;

                    while (retryCount < MAX_RETRIES && analysis == null) {
                        try {
                            analysis = analyzeTextWithOpenAI(openAiKey, paragraph);
                            batchResults.add(new ParagraphAnalysis(paragraph, analysis));
                        } catch (IOException e) {
                            retryCount++;
                            System.err.println("Retry " + retryCount + " failed: " + e.getMessage());

                            if (e.getMessage().contains("429")) {
                                System.out.println("Rate limit hit. Waiting 30 seconds...");
                                Thread.sleep(30000);
                            } else if (retryCount < MAX_RETRIES) {
                                Thread.sleep(delayMs);
                                delayMs *= 2; // Exponential backoff
                            } else {
                                batchResults.add(new ParagraphAnalysis(paragraph, "Error: Failed after retries — " + e.getMessage()));
                            }
                        }
                    }
                }

                results.addAll(batchResults);

                System.out.println("Waiting " + BASE_BATCH_DELAY_MS + "ms before next batch...");
                Thread.sleep(BASE_BATCH_DELAY_MS);
            }

            String outputDir = System.getProperty("user.dir");
            String htmlPath = outputDir + "/grammar_report.html";

            generateHtmlReport(results, htmlPath);

            System.out.println("\nReport generated at: " + htmlPath);

        } finally {
            if (driver != null) driver.quit();
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    private static String analyzeTextWithOpenAI(String apiKey, String paragraphText) throws IOException {
    	String prompt = "You are an expert English language assistant. For the following paragraph, perform these tasks:\n\n"
    	        + "1. Identify all spelling, grammar, and punctuation errors.\n"
    	        + "2. For each error, provide the incorrect text segment, the corrected version, and a brief explanation.\n"
    	        + "3. Provide a corrected version of the full paragraph.\n"
    	        + "4. Output the analysis in this exact format:\n\n"
    	        + "Errors:\n"
    	        + "- [Error 1]: \"<incorrect text>\" should be \"<corrected text>\" — <brief explanation>.\n"
    	        + "- [Error 2]: ...\n\n"
    	        + "Corrected Paragraph:\n"
    	        + "<corrected full paragraph text here>\n\n"
    	        + "Paragraph:\n"
    	        + paragraphText
    	        + "\n\nIf there are no errors, please respond with 'No errors found.'";

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "gpt-4o-mini");
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.1);

        RequestBody body = RequestBody.create(requestBody.toString(), MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("OpenAI API error: " + response.code() + " " + response.message());
            }
            String respBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(respBody, JsonObject.class);
            JsonArray choices = jsonResponse.getAsJsonArray("choices");

            return choices.get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();
        }
    }

    private static void generateHtmlReport(List<ParagraphAnalysis> analyses, String outputPath) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><title>Grammar & Spellcheck Report</title>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif; margin:20px;}")
                .append("table{border-collapse:collapse; width:100%;}")
                .append("th,td{border:1px solid #ccc; padding:10px; vertical-align:top;}")
                .append("th{background:#f4f4f4;}")
                .append("pre{white-space:pre-wrap; word-wrap:break-word;}")
                .append(".error {color: red;}")
                .append("</style></head><body>")
                .append("<h1>AI Grammar & Spellcheck Report</h1>")
                .append("<table><thead><tr><th>Original Paragraph</th><th>AI Analysis</th></tr></thead><tbody>");

        for (ParagraphAnalysis analysis : analyses) {
            html.append("<tr><td><pre>")
                    .append(escapeHtml(analysis.originalText))
                    .append("</pre></td><td><pre>")
                    .append(analysis.aiResponse.startsWith("Error:") ? "<span class='error'>" + escapeHtml(analysis.aiResponse) + "</span>" : escapeHtml(analysis.aiResponse))
                    .append("</pre></td></tr>");
        }

        html.append("</tbody></table></body></html>");

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static class ParagraphAnalysis {
        final String originalText;
        final String aiResponse;

        ParagraphAnalysis(String originalText, String aiResponse) {
            this.originalText = originalText;
            this.aiResponse = aiResponse;
        }
    }
}
