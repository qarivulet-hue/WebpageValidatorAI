package WebpageValidatorAIPKG;


import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.rules.RuleMatch;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ConTestNG{
    WebDriver driver;  // made package-private for TestNG class to quit driver
    private WebDriverWait wait;
    private List<WebsiteData> websiteDataList = new ArrayList<>();
    private static final int LINK_CHECK_THREADS = 10;

    // Font weight mapping
    private static final Map<String, String> FONT_WEIGHT_NAMES = new HashMap<>();
    static {
        FONT_WEIGHT_NAMES.put("100", "Thin");
        FONT_WEIGHT_NAMES.put("200", "Extra Light");
        FONT_WEIGHT_NAMES.put("300", "Light");
        FONT_WEIGHT_NAMES.put("400", "Normal / Regular");
        FONT_WEIGHT_NAMES.put("500", "Medium");
        FONT_WEIGHT_NAMES.put("600", "Semi Bold");
        FONT_WEIGHT_NAMES.put("700", "Bold");
        FONT_WEIGHT_NAMES.put("800", "Extra Bold");
        FONT_WEIGHT_NAMES.put("900", "Extra Bold");
    }

    public static void muteLogs() {
        LogManager.getLogManager().reset();
        Logger globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        globalLogger.setLevel(Level.WARNING);

        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.WARNING);

        Logger nettyLogger = Logger.getLogger("io.netty");
        nettyLogger.setLevel(Level.WARNING);

        Logger asyncHttpLogger = Logger.getLogger("org.asynchttpclient");
        asyncHttpLogger.setLevel(Level.WARNING);

        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setLevel(Level.WARNING);
            }
        }
    }

    public void runQAAudit(String url) throws Exception {
        if (driver == null) {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--headless=new");
            driver = new ChromeDriver(options);
            driver.manage().window().setSize(new Dimension(1920, 1200));
            wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        }

        driver.get(url);

        String exactUrl = driver.getCurrentUrl();
        String title = driver.getTitle();

        String metaDescription = "";
        try {
            WebElement metaDesc = driver.findElement(By.xpath("//meta[@name='description']"));
            metaDescription = metaDesc.getAttribute("content");
        } catch (NoSuchElementException ex) {
            metaDescription = "MISSING";
        }

        Map<String, List<HeadingElement>> headers = new HashMap<>();
        for (int i = 1; i <= 6; i++) {
            String tag = "h" + i;
            List<WebElement> elements = driver.findElements(By.tagName(tag));
            List<HeadingElement> headingElements = new ArrayList<>();
            for (WebElement el : elements) {
                headingElements.add(extractHeadingStyle(el, tag));
            }
            headers.put(tag, headingElements);
        }

        // Collect buttons and links separately and ensure uniqueness
        List<ButtonOrLink> buttons = new ArrayList<>();
        Set<String> processedElements = new HashSet<>(); // To avoid duplicates

        // Process buttons
        for (WebElement btn : driver.findElements(By.tagName("button"))) {
            String elementId = btn.getAttribute("id") + btn.getLocation().toString(); // Unique identifier
            if (!processedElements.contains(elementId)) {
                ButtonOrLink button = extractButtonOrLink(btn, "button");
                if (button.text != null && !button.text.isEmpty() && !button.text.contains("Icon")) { // Filter out invalid entries
                    buttons.add(button);
                    processedElements.add(elementId);
                }
            }
        }

        // Process links
        for (WebElement link : driver.findElements(By.tagName("a"))) {
            String elementId = link.getAttribute("id") + link.getLocation().toString(); // Unique identifier
            if (!processedElements.contains(elementId)) {
                ButtonOrLink button = extractButtonOrLink(link, "a");
                if (button.text != null && !button.text.isEmpty() && !button.text.contains("Icon")) { // Filter out invalid entries
                    buttons.add(button);
                    processedElements.add(elementId);
                }
            }
        }

        List<ParagraphStyle> paragraphs = new ArrayList<>();
        for (WebElement para : driver.findElements(By.tagName("p"))) {
            paragraphs.add(extractParagraphStyle(para));
        }

        String visibleText = driver.findElement(By.tagName("body")).getText();
        List<String> textChunks = Arrays.asList(visibleText.split("\\n+"));
        List<GrammarIssue> grammarIssues = parallelGrammarCheck(textChunks);

        List<BrokenLink> brokenLinks = parallelBrokenLinkCheck(driver.findElements(By.tagName("a")));

        WebsiteData websiteData = new WebsiteData(exactUrl, title, metaDescription, headers, buttons, paragraphs,
                grammarIssues, brokenLinks);
        websiteDataList.add(websiteData);

        generateDashboardHTML();
    }

    private List<GrammarIssue> parallelGrammarCheck(List<String> textChunks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<List<GrammarIssue>>> futures = new ArrayList<>();
        for (String chunk : textChunks) {
            if (chunk.trim().isEmpty())
                continue;
            futures.add(executor.submit(() -> grammarCheck(chunk)));
        }
        List<GrammarIssue> allIssues = new ArrayList<>();
        for (Future<List<GrammarIssue>> f : futures) {
            try {
                allIssues.addAll(f.get());
            } catch (Exception ignored) {
            }
        }
        executor.shutdown();
        return allIssues;
    }

    private List<BrokenLink> parallelBrokenLinkCheck(List<WebElement> links) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(LINK_CHECK_THREADS);
        List<Future<BrokenLink>> futures = new ArrayList<>();
        for (WebElement link : links) {
            String linkUrl = link.getAttribute("href");
            String linkText = link.getText();
            if (linkUrl != null && linkUrl.startsWith("http")) {
                futures.add(executor.submit(() -> {
                    int status = getHttpStatus(linkUrl);
                    if (status >= 400) {
                        return new BrokenLink(linkText, linkUrl, String.valueOf(status));
                    }
                    return null;
                }));
            }
        }
        List<BrokenLink> brokenLinks = new ArrayList<>();
        for (Future<BrokenLink> f : futures) {
            try {
                BrokenLink bl = f.get();
                if (bl != null)
                    brokenLinks.add(bl);
            } catch (Exception ignored) {
            }
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        return brokenLinks;
    }

    private HeadingElement extractHeadingStyle(WebElement el, String tag) {
        String text = el.getText();
        if (text == null || text.trim().isEmpty()) {
            text = el.getAttribute("textContent");
            if (text == null) {
                text = "";
            }
        }
        text = text.trim().replaceAll("\\s+", " ");
        text = text.replaceAll("<[^>]+>", "");
        if (text.isEmpty()) {
            text = "";
        }

        return new HeadingElement(tag, text, el.getCssValue("font-family"), el.getCssValue("font-size"),
                el.getCssValue("font-weight"), el.getCssValue("line-height"), el.getCssValue("letter-spacing"),
                el.getCssValue("color"));
    }

    private ButtonOrLink extractButtonOrLink(WebElement el, String type) {
        String text = extractTextFromElement(el).trim();
        if (text.isEmpty()) {
            // Use alt text from the first image as a fallback
            List<WebElement> images = el.findElements(By.tagName("img"));
            for (WebElement img : images) {
                String altText = img.getAttribute("alt");
                if (altText != null && !altText.isEmpty()) {
                    text = altText.trim();
                    break; // Use the first meaningful alt text
                }
            }
            if (text.isEmpty()) {
                // Use aria-label or title as a fallback
                String ariaLabel = el.getAttribute("aria-label");
                if (ariaLabel != null && !ariaLabel.isEmpty()) {
                    text = ariaLabel.trim();
                } else {
                    String titleAttr = el.getAttribute("title");
                    if (titleAttr != null && !titleAttr.isEmpty()) {
                        text = titleAttr.trim();
                    }
                }
            }
            if (text.isEmpty()) {
                text = type.equals("button") ? "Icon Button" : "Icon Link"; // Descriptive fallback
            }
        }
        return new ButtonOrLink(type, text, el.getCssValue("background-color"), el.getCssValue("color"),
                el.getCssValue("font-size"), el.getCssValue("font-weight"), el.getCssValue("padding"),
                el.getCssValue("border-radius"), el.getCssValue("line-height"), el.getCssValue("letter-spacing"),
                el.getAttribute("href"));
    }

    private String extractTextFromElement(WebElement element) {
        StringBuilder text = new StringBuilder();
        try {
            // Get text directly from the element
            String elementText = element.getText().trim();
            if (!elementText.isEmpty()) {
                text.append(elementText);
                return text.toString(); // Return early to avoid further checks
            }

            // Check for text content attribute
            String textContent = element.getAttribute("textContent");
            if (textContent != null && !textContent.trim().isEmpty()) {
                text.append(textContent.trim());
                return text.toString(); // Return early
            }

            // Fallback to innerText
            String innerText = element.getAttribute("innerText");
            if (innerText != null && !innerText.trim().isEmpty()) {
                text.append(innerText.trim().replaceAll("<[^>]+>", ""));
                return text.toString(); // Return early
            }
        } catch (Exception e) {
            // Ignore and proceed to fallback
        }
        return ""; // Return empty if no text is found
    }

    private ParagraphStyle extractParagraphStyle(WebElement el) {
        return new ParagraphStyle(el.getText(), el.getCssValue("font-family"), el.getCssValue("font-size"),
                el.getCssValue("font-weight"), el.getCssValue("line-height"), el.getCssValue("letter-spacing"),
                el.getCssValue("color"));
    }

    private List<GrammarIssue> grammarCheck(String text) throws Exception {
        List<GrammarIssue> issues = new ArrayList<>();
        try {
            JLanguageTool langTool = new JLanguageTool(new AmericanEnglish());
            List<RuleMatch> matches = langTool.check(text);
            for (RuleMatch match : matches) {
                String context = text.substring(match.getFromPos(), match.getToPos());
                String suggestion = match.getSuggestedReplacements().isEmpty() ? ""
                        : String.join(", ", match.getSuggestedReplacements());
                issues.add(new GrammarIssue(context, suggestion, match.getMessage()));
            }
        } catch (Throwable e) {
            // ignore grammar errors
        }
        return issues;
    }

    private int getHttpStatus(String urlStr) {
        int retries = 3;
        while (retries > 0) {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                return connection.getResponseCode();
            } catch (Exception e) {
                retries--;
                if (retries == 0) {
                    return 400;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return 400;
    }

    public int calculateSeoScore(WebsiteData data) {
        int score = 100;

        if (data.title == null || data.title.trim().isEmpty() || data.title.length() > 60) {
            score -= 20;
        }

        if ("MISSING".equals(data.metaDescription) || data.metaDescription.length() > 160) {
            score -= 20;
        }

        score -= data.brokenLinks.size() * 10;

        score -= data.grammarIssues.size() * 5;

        int h1Count = data.headers.getOrDefault("h1", Collections.emptyList()).size();
        if (h1Count != 1) {
            score -= 15;
        }

        return Math.max(0, score);
    }

    public String generateSeoScoreDetails(WebsiteData data) {
        StringBuilder details = new StringBuilder();
        int h1Count = data.headers.getOrDefault("h1", Collections.emptyList()).size();
        String headingStatus = h1Count == 1 ? "Good heading hierarchy"
                : (h1Count == 0 ? "Missing H1" : "Multiple H1s detected");
        details.append(escapeHtml(headingStatus)).append("|");
        String contentQuality = data.grammarIssues.isEmpty() ? "No grammar issues"
                : data.grammarIssues.size() + " minor grammar issues";
        details.append(escapeHtml(contentQuality)).append("|");
        String linkHealth = data.brokenLinks.isEmpty() ? "No broken links" : data.brokenLinks.size() + " broken links";
        details.append(escapeHtml(linkHealth));
        return details.toString();
    }

    private void generateDashboardHTML() throws IOException {
        // (Your existing HTML generation code here, omitted for brevity)
        // Write your existing generateDashboardHTML method code here exactly as you have it
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String cssColorToHex(String cssColor) {
        if (cssColor == null || cssColor.trim().isEmpty()) return "#000000";
        if (cssColor.trim().startsWith("#")) return cssColor.trim();
        try {
            if (cssColor.startsWith("rgb(") || cssColor.startsWith("rgba(")) {
                String nums = cssColor.substring(cssColor.indexOf('(') + 1, cssColor.indexOf(')'));
                String[] parts = nums.split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return String.format("#%02x%02x%02x", r, g, b);
            }
        } catch (Exception e) {
            return "#000000";
        }
        return "#000000";
    }

    // Classes for data holding (unchanged) ...

    static class WebsiteData {
        String url;
        String title;
        String metaDescription;
        Map<String, List<HeadingElement>> headers;
        List<ButtonOrLink> buttons;
        List<ParagraphStyle> paragraphs;
        List<GrammarIssue> grammarIssues;
        List<BrokenLink> brokenLinks;

        public WebsiteData(String url, String title, String metaDescription, Map<String, List<HeadingElement>> headers,
                List<ButtonOrLink> buttons, List<ParagraphStyle> paragraphs, List<GrammarIssue> grammarIssues,
                List<BrokenLink> brokenLinks) {
            this.url = url;
            this.title = title;
            this.metaDescription = metaDescription;
            this.headers = headers;
            this.buttons = buttons;
            this.paragraphs = paragraphs;
            this.grammarIssues = grammarIssues;
            this.brokenLinks = brokenLinks;
        }
    }

    static class HeadingElement {
        String tag, text, fontFamily, fontSize, fontWeight, lineHeight, letterSpacing, color;

        public HeadingElement(String tag, String text, String fontFamily, String fontSize, String fontWeight,
                String lineHeight, String letterSpacing, String color) {
            this.tag = tag;
            this.text = text;
            this.fontFamily = fontFamily;
            this.fontSize = fontSize;
            this.fontWeight = fontWeight;
            this.lineHeight = lineHeight;
            this.letterSpacing = letterSpacing;
            this.color = color;
        }
    }

    static class ButtonOrLink {
        String type, text, buttonColor, textColor, fontSize, fontWeight, padding, borderRadius, lineHeight, letterSpacing, href;

        public ButtonOrLink(String type, String text, String buttonColor, String textColor, String fontSize, String fontWeight,
                String padding, String borderRadius, String lineHeight, String letterSpacing, String href) {
            this.type = type;
            this.text = text;
            this.buttonColor = buttonColor;
            this.textColor = textColor;
            this.fontSize = fontSize;
            this.fontWeight = fontWeight;
            this.padding = padding;
            this.borderRadius = borderRadius;
            this.lineHeight = lineHeight;
            this.letterSpacing = letterSpacing;
            this.href = href;
        }
    }

    static class ParagraphStyle {
        String text, fontFamily, fontSize, fontWeight, lineHeight, letterSpacing, color;

        public ParagraphStyle(String text, String fontFamily, String fontSize, String fontWeight, String lineHeight,
                String letterSpacing, String color) {
            this.text = text;
            this.fontFamily = fontFamily;
            this.fontSize = fontSize;
            this.fontWeight = fontWeight;
            this.lineHeight = lineHeight;
            this.letterSpacing = letterSpacing;
            this.color = color;
        }
    }

    static class GrammarIssue {
        String context, suggestion, message;

        public GrammarIssue(String context, String suggestion, String message) {
            this.context = context;
            this.suggestion = suggestion;
            this.message = message;
        }
    }

    static class BrokenLink {
        String text, url, status;

        public BrokenLink(String text, String url, String status) {
            this.text = text;
            this.url = url;
            this.status = status;
        }
    }
}