package Cursor_Sample;
import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.rules.RuleMatch;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;
import io.grpc.netty.shaded.io.netty.handler.timeout.TimeoutException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Webpage_Validator_QA {


    private WebDriver driver;
    private WebDriverWait wait;
    private List<WebsiteData> websiteDataList = new ArrayList<>();
    private static final int LINK_CHECK_THREADS = 10;
    private static final Logger LOGGER = Logger.getLogger(Webpage_Validator_QA.class.getName());

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
        globalLogger.setLevel(Level.INFO);

        Logger seleniumLogger = Logger.getLogger("org.openqa.selenium");
        seleniumLogger.setLevel(Level.INFO);

        Logger nettyLogger = Logger.getLogger("io.netty");
        nettyLogger.setLevel(Level.INFO);

        Logger asyncHttpLogger = Logger.getLogger("org.asynchttpclient");
        asyncHttpLogger.setLevel(Level.INFO);

        for (Handler h : Logger.getLogger("").getHandlers()) {
            if (h instanceof ConsoleHandler) {
                h.setLevel(Level.INFO);
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

        try {
            driver.get(url);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//p | //a")));
        } catch (TimeoutException e) {
            LOGGER.warning("Failed to load page body for URL: " + url + ". Error: " + e.getMessage());
            websiteDataList.add(new WebsiteData(url, "No data found", "No data found", new HashMap<>(),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
            generateDashboardHTML();
            return;
        }

        String exactUrl = driver.getCurrentUrl();
        String title = "No data found";
        try {
            title = driver.getTitle() != null ? driver.getTitle() : "No data found";
        } catch (Exception e) {
            LOGGER.warning("Failed to retrieve page title for URL: " + url + ". Error: " + e.getMessage());
        }
        String metaDescription = "No data found";
        try {
            WebElement metaDesc = driver.findElement(By.xpath("//meta[@name='description']"));
            metaDescription = metaDesc.getAttribute("content") != null ? metaDesc.getAttribute("content")
                    : "No data found";
        } catch (NoSuchElementException ex) {
            LOGGER.warning("Meta description not found for URL: " + url);
        } catch (Exception e) {
            LOGGER.warning("Error retrieving meta description for URL: " + url + ". Error: " + e.getMessage());
        }

        Map<String, List<HeadingElement>> headers = new HashMap<>();
        for (int i = 1; i <= 6; i++) {
            String tag = "h" + i;
            List<HeadingElement> headingElements = new ArrayList<>();
            try {
                List<WebElement> elements = driver.findElements(By.tagName(tag));
                for (WebElement el : elements) {
                    headingElements.add(extractHeadingStyle(el, tag));
                }
            } catch (Exception e) {
                LOGGER.warning("Error retrieving " + tag + " headings for URL: " + url + ". Error: " + e.getMessage());
            }
            headers.put(tag, headingElements);
        }

        List<ButtonOrLink> buttons = new ArrayList<>();
        Set<String> processedElements = new HashSet<>();

        try {
            for (WebElement btn : driver.findElements(By.tagName("button"))) {
                String elementId = btn.getAttribute("id") + btn.getLocation().toString();
                if (!processedElements.contains(elementId)) {
                    ButtonOrLink button = extractButtonOrLink(btn, "button");
                    if (button.text != null && !button.text.isEmpty() && !button.text.contains("Icon")) {
                        buttons.add(button);
                        processedElements.add(elementId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error retrieving buttons for URL: " + url + ". Error: " + e.getMessage());
        }

        try {
            List<WebElement> links = driver.findElements(By.tagName("a"));
            System.out.println("Total links found: " + links.size());
            for (WebElement link : links) {
                String href = link.getAttribute("href");
                String text = link.getText();
                System.out.println("Link href: " + href + ", Text: " + text);
                String elementId = link.getAttribute("id") + link.getLocation().toString();
                if (!processedElements.contains(elementId)) {
                    ButtonOrLink button = extractButtonOrLink(link, "a");
                    if (button.text != null && !button.text.isEmpty()) {
                        buttons.add(button);
                        processedElements.add(elementId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error retrieving links for URL: " + url + ". Error: " + e.getMessage());
        }

        List<ParagraphStyle> paragraphs = new ArrayList<>();
        try {
            List<WebElement> allParagraphs = driver.findElements(By.xpath("//p"));
            System.out.println("Total <p> tags found: " + allParagraphs.size());
            for (WebElement para : driver.findElements(By.tagName("p"))) {
                paragraphs.add(extractParagraphStyle(para));
            }
        } catch (Exception e) {
            LOGGER.warning("Error retrieving paragraphs for URL: " + url + ". Error: " + e.getMessage());
        }

        // Comprehensive text extraction for grammar checking
        List<String> allTextContent = new ArrayList<>();
        
        // Extract text from paragraphs
        try {
            List<WebElement> paragraphElements = driver.findElements(By.tagName("p"));
            for (WebElement para : paragraphElements) {
                String text = para.getText();
                if (text != null && !text.trim().isEmpty()) {
                    allTextContent.add("PARAGRAPH: " + text.trim());
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting paragraph text: " + e.getMessage());
        }
        
        // Extract text from headings
        try {
            for (int i = 1; i <= 6; i++) {
                List<WebElement> headings = driver.findElements(By.tagName("h" + i));
                for (WebElement heading : headings) {
                    String text = heading.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        allTextContent.add("HEADING H" + i + ": " + text.trim());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting heading text: " + e.getMessage());
        }
        
        // Extract text from buttons and links
        try {
            List<WebElement> buttonElements = driver.findElements(By.tagName("button"));
            for (WebElement button : buttonElements) {
                String text = button.getText();
                if (text != null && !text.trim().isEmpty() && !text.contains("Icon")) {
                    allTextContent.add("BUTTON: " + text.trim());
                }
            }
            
            List<WebElement> linkElements = driver.findElements(By.tagName("a"));
            for (WebElement link : linkElements) {
                String text = link.getText();
                if (text != null && !text.trim().isEmpty()) {
                    allTextContent.add("LINK: " + text.trim());
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting button/link text: " + e.getMessage());
        }
        
        // Extract text from other common text elements
        try {
            String[] textElements = {"span", "div", "label", "li", "td", "th"};
            for (String tag : textElements) {
                List<WebElement> elements = driver.findElements(By.tagName(tag));
                for (WebElement element : elements) {
                    String text = element.getText();
                    if (text != null && !text.trim().isEmpty() && text.length() > 3) {
                        allTextContent.add(tag.toUpperCase() + ": " + text.trim());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting other text elements: " + e.getMessage());
        }
        
        // Extract meta description for grammar checking
        try {
            WebElement metaDesc = driver.findElement(By.xpath("//meta[@name='description']"));
            String metaText = metaDesc.getAttribute("content");
            if (metaText != null && !metaText.trim().isEmpty()) {
                allTextContent.add("META DESCRIPTION: " + metaText.trim());
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting meta description: " + e.getMessage());
        }
        
        // Extract page title for grammar checking
        try {
            String titleText = driver.getTitle();
            if (titleText != null && !titleText.trim().isEmpty()) {
                allTextContent.add("PAGE TITLE: " + titleText.trim());
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting page title: " + e.getMessage());
        }
        
        LOGGER.info("Extracted " + allTextContent.size() + " text elements for spelling checking");
        List<GrammarIssue> grammarIssues = parallelGrammarCheck(allTextContent);

        // Only check links that were already extracted and validated during the main processing
        List<WebElement> validLinks = new ArrayList<>();
        try {
            List<WebElement> allLinks = driver.findElements(By.tagName("a"));
            for (WebElement link : allLinks) {
                String href = link.getAttribute("href");
                if (href != null && !href.isEmpty() && !href.startsWith("javascript:") && !href.startsWith("mailto:") && !href.startsWith("tel:")) {
                    // Only include links that are visible and have valid href attributes
                    if (link.isDisplayed() && !href.equals("#") && !href.equals("javascript:void(0)")) {
                        validLinks.add(link);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error filtering valid links: " + e.getMessage());
        }
        
        List<BrokenLink> brokenLinks = parallelBrokenLinkCheck(validLinks, exactUrl);
        System.out.println("Broken links detected: " + brokenLinks);

        WebsiteData websiteData = new WebsiteData(exactUrl, title, metaDescription, headers, buttons, paragraphs,
                grammarIssues, brokenLinks);
        websiteDataList.add(websiteData);

        generateDashboardHTML();
    }

    private List<GrammarIssue> parallelGrammarCheck(List<String> textChunks) {
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
            } catch (Exception e) {
                LOGGER.warning("Error during grammar check: " + e.getMessage());
            }
        }
        executor.shutdown();
        return allIssues;
    }

    private List<BrokenLink> parallelBrokenLinkCheck(List<WebElement> links, String pageUrl) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(LINK_CHECK_THREADS);
        List<Future<BrokenLink>> futures = new ArrayList<>();
        
        LOGGER.info("Starting broken link check for " + links.size() + " links from page: " + pageUrl);
        
        for (WebElement link : links) {
            String linkUrl = link.getAttribute("href");
            String linkText = link.getText();
            
            if (linkUrl != null && !linkUrl.isEmpty()) {
                try {
                    URL baseUrl = new URL(pageUrl);
                    URL absoluteUrl = new URL(baseUrl, linkUrl); // Resolve relative URLs
                    String resolvedUrl = absoluteUrl.toString();
                    
                    // Additional validation to ensure we're only checking legitimate links
                    if (resolvedUrl.startsWith("http") && !resolvedUrl.contains("javascript:") && 
                        !resolvedUrl.contains("mailto:") && !resolvedUrl.contains("tel:")) {
                        
                        // Log the link being checked for debugging
                        LOGGER.info("Checking link: " + resolvedUrl + " (Text: " + linkText + ")");
                        
                        futures.add(executor.submit(() -> {
                            int status = getHttpStatus(resolvedUrl);
                            // Only capture true 404 errors
                            if (status == 404) {
                                LOGGER.info("404 broken link detected: " + resolvedUrl);
                                return new BrokenLink(linkText, resolvedUrl, String.valueOf(status), pageUrl);
                            }
                            return null;
                        }));
                    } else {
                        LOGGER.info("Skipping non-HTTP link: " + resolvedUrl);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Invalid URL format: " + linkUrl + ", Error: " + e.getMessage());
                }
            }
        }
        
        List<BrokenLink> brokenLinks = new ArrayList<>();
        for (Future<BrokenLink> f : futures) {
            try {
                BrokenLink bl = f.get();
                if (bl != null) {
                    brokenLinks.add(bl);
                }
            } catch (Exception e) {
                LOGGER.warning("Error checking broken links: " + e.getMessage());
            }
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        System.out.println("Broken links detected: " + brokenLinks);
        return brokenLinks;
    }

    private HeadingElement extractHeadingStyle(WebElement el, String tag) {
        String text = "Not available";
        try {
            text = el.getText();
            if (text == null || text.trim().isEmpty()) {
                text = el.getAttribute("textContent") != null
                        ? el.getAttribute("textContent").trim().replaceAll("\\s+", " ").replaceAll("<[^>]+>", "")
                        : "Not available";
            }
            text = text.trim().replaceAll("\\s+", " ");
            if (text.isEmpty()) {
                text = "Not available";
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting text for heading " + tag + ": " + e.getMessage());
        }

        String fontFamily = "Not available";
        String fontSize = "Not available";
        String fontWeight = "Not available";
        String lineHeight = "Not available";
        String letterSpacing = "Not available";
        String color = "Not available";
        try {
            fontFamily = el.getCssValue("font-family") != null ? el.getCssValue("font-family") : "Not available";
            fontSize = el.getCssValue("font-size") != null ? el.getCssValue("font-size") : "Not available";
            fontWeight = el.getCssValue("font-weight") != null ? el.getCssValue("font-weight") : "Not available";
            lineHeight = el.getCssValue("line-height") != null ? el.getCssValue("line-height") : "Not available";
            letterSpacing = el.getCssValue("letter-spacing") != null ? el.getCssValue("letter-spacing")
                    : "Not available";
            color = el.getCssValue("color") != null ? el.getCssValue("color") : "Not available";
        } catch (Exception e) {
            LOGGER.warning("Error extracting CSS properties for heading " + tag + ": " + e.getMessage());
        }

        return new HeadingElement(tag, text, fontFamily, fontSize, fontWeight, lineHeight, letterSpacing, color);
    }

    private ButtonOrLink extractButtonOrLink(WebElement el, String type) {
        String text = extractTextFromElement(el).trim();
        String ariaLabel = "Not available";
        String imgAlt = null;
        boolean isImageLink = false;

        try {
            String extractedAriaLabel = el.getAttribute("aria-label");
            if (extractedAriaLabel != null && !extractedAriaLabel.trim().isEmpty()) {
                ariaLabel = extractedAriaLabel.trim();
                LOGGER.info("Extracted aria-label for " + type + ": " + ariaLabel);
            } else {
                LOGGER.info("No aria-label found for " + type + " at location: " + el.getLocation());
            }

            if (text.isEmpty() || type.equals("a")) {
                List<WebElement> images = el.findElements(By.tagName("img"));
                if (!images.isEmpty()) {
                    isImageLink = true;
                    WebElement img = images.get(0);
                    String altText = img.getAttribute("alt");
                    if (altText != null && !altText.trim().isEmpty()) {
                        text = "ALT: " + altText.trim();
                        imgAlt = altText.trim();
                    } else {
                        String src = img.getAttribute("src");
                        String fileName = src != null && src.contains("/") ? src.substring(src.lastIndexOf("/") + 1)
                                : "img";
                        text = "ALT: " + (fileName.isEmpty() ? "img" : fileName);
                        imgAlt = "Not available";
                    }
                } else {
                    String titleAttr = el.getAttribute("title");
                    if (titleAttr != null && !titleAttr.isEmpty()) {
                        text = titleAttr.trim();
                    } else if (text.isEmpty()) {
                        text = type.equals("button") ? "Icon Button" : "Icon Link";
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting text or aria-label for " + type + ": " + e.getMessage());
            text = "Not available";
        }

        String buttonColor = "Not available";
        String textColor = "Not available";
        String fontSize = "Not available";
        String fontWeight = "Not available";
        String padding = "Not available";
        String borderRadius = "Not available";
        String lineHeight = "Not available";
        String letterSpacing = "Not available";
        String href = type.equals("a") ? "Not available" : "";
        String fontFamily = "Not available";
        try {
            buttonColor = el.getCssValue("background-color") != null ? el.getCssValue("background-color")
                    : "Not available";
            textColor = el.getCssValue("color") != null ? el.getCssValue("color") : "Not available";
            fontSize = el.getCssValue("font-size") != null ? el.getCssValue("font-size") : "Not available";
            fontWeight = el.getCssValue("font-weight") != null ? el.getCssValue("font-weight") : "Not available";
            padding = el.getCssValue("padding") != null ? el.getCssValue("padding") : "Not available";
            borderRadius = el.getCssValue("border-radius") != null ? el.getCssValue("border-radius") : "Not available";
            lineHeight = el.getCssValue("line-height") != null ? el.getCssValue("line-height") : "Not available";
            letterSpacing = el.getCssValue("letter-spacing") != null ? el.getCssValue("letter-spacing")
                    : "Not available";
            fontFamily = el.getCssValue("font-family") != null ? el.getCssValue("font-family") : "Not available";
            if (type.equals("a")) {
                href = el.getAttribute("href") != null ? el.getAttribute("href") : "Not available";
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting CSS properties for " + type + ": " + e.getMessage());
        }

        return new ButtonOrLink(type, text, buttonColor, textColor, fontSize, fontWeight, padding, borderRadius,
                lineHeight, letterSpacing, href, ariaLabel, fontFamily, imgAlt, isImageLink);
    }

    private String extractTextFromElement(WebElement element) {
        StringBuilder text = new StringBuilder();
        try {
            String elementText = element.getText().trim();
            if (!elementText.isEmpty()) {
                text.append(elementText);
                return text.toString();
            }

            String textContent = element.getAttribute("textContent");
            if (textContent != null && !textContent.trim().isEmpty()) {
                text.append(textContent.trim());
                return text.toString();
            }

            String innerText = element.getAttribute("innerText");
            if (innerText != null && !innerText.trim().isEmpty()) {
                text.append(innerText.trim().replaceAll("<[^>]+>", ""));
                return text.toString();
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting text from element: " + e.getMessage());
        }
        return "Not available";
    }

    private ParagraphStyle extractParagraphStyle(WebElement el) {
        String text = "Not available";
        try {
            text = el.getText() != null ? el.getText().trim() : "Not available";
            if (text.isEmpty()) {
                text = el.getAttribute("textContent") != null
                        ? el.getAttribute("textContent").trim().replaceAll("\\s+", " ").replaceAll("<[^>]+>", "")
                        : "Not available";
            }
            if (text.isEmpty()) {
                text = "Not available";
            }
        } catch (Exception e) {
            LOGGER.warning("Error extracting text for paragraph: " + e.getMessage());
        }

        String fontFamily = "Not available";
        String fontSize = "Not available";
        String fontWeight = "Not available";
        String lineHeight = "Not available";
        String letterSpacing = "Not available";
        String color = "Not available";
        try {
            fontFamily = el.getCssValue("font-family") != null ? el.getCssValue("font-family") : "Not available";
            fontSize = el.getCssValue("font-size") != null ? el.getCssValue("font-size") : "Not available";
            fontWeight = el.getCssValue("font-weight") != null ? el.getCssValue("font-weight") : "Not available";
            lineHeight = el.getCssValue("line-height") != null ? el.getCssValue("line-height") : "Not available";
            letterSpacing = el.getCssValue("letter-spacing") != null ? el.getCssValue("letter-spacing")
                    : "Not available";
            color = el.getCssValue("color") != null ? el.getCssValue("color") : "Not available";
        } catch (Exception e) {
            LOGGER.warning("Error extracting CSS properties for paragraph: " + e.getMessage());
        }

        return new ParagraphStyle(text, fontFamily, fontSize, fontWeight, lineHeight, letterSpacing, color);
    }

    private List<GrammarIssue> grammarCheck(String text) {
        List<GrammarIssue> issues = new ArrayList<>();
        try {
            // Extract element type and actual text content
            String elementType = "UNKNOWN";
            String actualText = text;
            
            if (text.contains(":")) {
                String[] parts = text.split(":", 2);
                if (parts.length == 2) {
                    elementType = parts[0].trim();
                    actualText = parts[1].trim();
                }
            }
            
            // Skip very short text or single words
            if (actualText.length() < 4) {
                return issues;
            }
            
            JLanguageTool langTool = new JLanguageTool(new AmericanEnglish());
            
            // Disable all rules except spelling rules
            langTool.disableRule("WHITESPACE_RULE");
            langTool.disableRule("COMMA_PARENTHESIS_WHITESPACE");
            langTool.disableRule("DOUBLE_PUNCTUATION");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END2");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END3");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END4");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END5");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END6");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END7");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END8");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END9");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END10");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END11");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END12");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END13");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END14");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END15");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END16");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END17");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END18");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END19");
            langTool.disableRule("PUNCTUATION_PARAGRAPH_END20");
            
            // Disable grammar rules but keep spelling rules
            langTool.disableRule("EN_A_VS_AN");
            langTool.disableRule("EN_AGREEMENT");
            langTool.disableRule("EN_AGREEMENT_SENT_START");
            langTool.disableRule("EN_AGREEMENT_SENT_START2");
            langTool.disableRule("EN_AGREEMENT_SENT_START3");
            langTool.disableRule("EN_AGREEMENT_SENT_START4");
            langTool.disableRule("EN_AGREEMENT_SENT_START5");
            langTool.disableRule("EN_AGREEMENT_SENT_START6");
            langTool.disableRule("EN_AGREEMENT_SENT_START7");
            langTool.disableRule("EN_AGREEMENT_SENT_START8");
            langTool.disableRule("EN_AGREEMENT_SENT_START9");
            langTool.disableRule("EN_AGREEMENT_SENT_START10");
            langTool.disableRule("EN_AGREEMENT_SENT_START11");
            langTool.disableRule("EN_AGREEMENT_SENT_START12");
            langTool.disableRule("EN_AGREEMENT_SENT_START13");
            langTool.disableRule("EN_AGREEMENT_SENT_START14");
            langTool.disableRule("EN_AGREEMENT_SENT_START15");
            langTool.disableRule("EN_AGREEMENT_SENT_START16");
            langTool.disableRule("EN_AGREEMENT_SENT_START17");
            langTool.disableRule("EN_AGREEMENT_SENT_START18");
            langTool.disableRule("EN_AGREEMENT_SENT_START19");
            langTool.disableRule("EN_AGREEMENT_SENT_START20");
            
            List<RuleMatch> matches = langTool.check(actualText);
            
            for (RuleMatch match : matches) {
                // Only process spelling-related rules
                String ruleId = match.getRule().getId();
                if (ruleId.contains("SPELL") || ruleId.contains("MORFOLOGIK") || ruleId.contains("HUNSPELL")) {
                    String context = actualText.substring(match.getFromPos(), match.getToPos());
                    String suggestion = match.getSuggestedReplacements().isEmpty() ? "No suggestions available"
                            : String.join(", ", match.getSuggestedReplacements());
                    
                    // Create a more detailed message
                    String detailedMessage = "Spelling error in " + elementType;
                    
                    issues.add(new GrammarIssue(context, suggestion, detailedMessage, elementType, actualText));
                    LOGGER.info("Spelling error found in " + elementType + ": " + context + " -> " + suggestion);
                }
            }
        } catch (Throwable e) {
            LOGGER.warning("Spelling check failed for text: " + text + ". Error: " + e.getMessage());
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
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                int status = connection.getResponseCode();

                // Logging the status for debugging
                LOGGER.info("URL: " + urlStr + ", Status: " + status);

                // Only return 404 for actual 404 status codes
                if (status == 404) {
                    LOGGER.info("True 404 detected for URL: " + urlStr);
                    return 404;
                } else if (status == 200) {
                    // For status 200, check if content contains specific 404 error page indicators
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String content = reader.lines().collect(Collectors.joining());
                        // More specific 404 page detection - look for common 404 page patterns
                        if (content.contains("404 Not Found") || 
                            content.contains("Page Not Found") || 
                            content.contains("The page you requested was not found") ||
                            content.contains("Error 404") ||
                            content.contains("HTTP 404") ||
                            (content.contains("404") && content.contains("not found"))) {
                            LOGGER.info("Soft 404 detected for URL: " + urlStr);
                            return 404;
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Error reading content for soft 404 check: " + e.getMessage());
                    }
                }

                // Return the actual status code for all other cases
                return status;
            } catch (Exception e) {
                retries--;
                LOGGER.warning("Failed to get HTTP status for URL: " + urlStr + ", Retries left: " + retries + ", Error: " + e.getMessage());
                if (retries == 0) {
                    // Don't treat connection failures as 404s
                    return -1; // Return -1 for failed attempts to distinguish from valid status codes
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return -1; // Return -1 for failed attempts
    }

    private int calculateSeoScore(WebsiteData data) {
        int score = 100;

        if (data.title == null || data.title.trim().isEmpty() || data.title.equals("No data found")
                || data.title.length() > 60) {
            score -= 20;
        }

        if (data.metaDescription == null || data.metaDescription.equals("No data found")
                || data.metaDescription.length() > 160) {
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

    private String generateSeoScoreDetails(WebsiteData data) {
        StringBuilder details = new StringBuilder();
        int h1Count = data.headers.getOrDefault("h1", Collections.emptyList()).size();
        String headingStatus = h1Count == 1 ? "Good heading hierarchy"
                : (h1Count == 0 ? "Missing H1" : "Multiple H1s detected");
        details.append(escapeHtml(headingStatus)).append("|");
        String contentQuality = data.grammarIssues.isEmpty() ? "No grammar issues"
                : data.grammarIssues.size() + " minor grammar issues";
        details.append(escapeHtml(contentQuality)).append("|");
        String linkHealth = data.brokenLinks.isEmpty() ? "No broken links" : data.brokenLinks.size() + " 404 broken links";
        details.append(escapeHtml(linkHealth));
        return details.toString();
    }

    private void generateDashboardHTML() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\" />\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n");
        sb.append("<title>WebQA Dashboard</title>\n");
        sb.append("<style>\n");
        sb.append("@import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;600;700&display=swap');\n");
        sb.append("body { margin: 0; padding: 20px; background: #f9fafb; font-family: 'Roboto', sans-serif; color: #1a1a1a; }\n");
        sb.append(".dashboard-container { width: 1440px; margin: 0 auto; }\n");
        sb.append("header { font-weight: 700; font-size: 24px; color: #1a365d; display: flex; align-items: center; gap: 8px; margin-bottom: 24px; background-color: #e3e9f7; padding: 16px 20px; border-radius: 6px; justify-content: space-between; }\n");
        sb.append("header svg { width: 24px; height: 24px; fill: #1a365d; }\n");
        sb.append("header span { font-size: 16px; font-weight: 400; color: #777; }\n");
        sb.append(".analyze-bar { display: flex; gap: 12px; margin-bottom: 24px; align-items: center; justify-content: center; }\n");
        sb.append("input[type=\"url\"] { width: 80%; padding: 10px 12px; border-radius: 4px; border: 1px solid #ccc; font-size: 14px; color: #1a1a1a; }\n");
        sb.append("button { background: #1a365d; border: none; padding: 10px 20px; border-radius: 4px; color: #fff; font-weight: 600; font-size: 14px; cursor: pointer; width: 120px; }\n");
        sb.append("button:hover { background: #12172f; }\n");
        sb.append(".dashboard-section { background-color: #f3f6fb; padding: 16px 20px; border-radius: 6px; margin-bottom: 24px; color: #1a365d; font-weight: 600; font-size: 18px; }\n");
        sb.append(".dashboard-subheader { font-size: 14px; color: #666; font-weight: 400; }\n");
        sb.append(".tabs { display: flex; gap: 30px; border-bottom: 1px solid #eaeaea; margin-bottom: 24px; padding: 10px 0; }\n");
        sb.append(".tab { font-weight: 600; font-size: 16px; color: #444; padding-bottom: 12px; cursor: pointer; border-bottom: 3px solid transparent; }\n");
        sb.append(".tab.active { border-bottom-color: #1a365d; color: #1a365d; }\n");
        sb.append(".tab-content { background: #fff; border-radius: 12px; padding: 32px 40px; box-shadow: 0 4px 16px rgb(0 0 0 / 0.05); display: none; }\n");
        sb.append(".tab-content.active { display: block; }\n");
        sb.append(".seo-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 48px; }\n");
        sb.append(".seo-card { background: #fff; border-radius: 6px; padding: 20px; min-height: 80px; display: flex; flex-direction: column; justify-content: center; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05); }\n");
        sb.append(".seo-card h3 { font-size: 16px; font-weight: 700; color: #1a365d; margin-bottom: 8px; }\n");
        sb.append(".seo-card p { font-size: 14px; line-height: 1.4; color: #222; word-break: break-all; }\n");
        sb.append(".seo-card .ok { color: #4caf50; font-weight: 700; margin-left: 8px; }\n");
        sb.append(".seo-card .warning { color: #e07a5f; font-weight: 600; display: block; margin-top: 4px; }\n");
        sb.append(".seo-score-container { text-align: center; margin-bottom: 48px; }\n");
        sb.append(".score-circle { width: 140px; height: 140px; margin: 0 auto 12px; border-radius: 50%; border: 6px solid #e3e9f7; font-size: 44px; font-weight: 700; color: #1a365d; line-height: 140px; position: relative; }\n");
        sb.append(".score-circle span { font-weight: 400; font-size: 18px; color: #444; display: block; }\n");
        sb.append(".score-metrics { display: flex; justify-content: center; gap: 32px; font-size: 14px; font-weight: 600; }\n");
        sb.append(".score-box { border-radius: 6px; padding: 12px 20px; min-width: 150px; text-align: center; }\n");
        sb.append(".score-headings { background: #e3ebfb; color: #3a6fd1; }\n");
        sb.append(".score-content { background: #d3f1d8; color: #4caf50; }\n");
        sb.append(".score-link { background: #fbe4d2; color: #f4a261; }\n");
        sb.append(".heading-structure { display: flex; gap: 18px; margin-bottom: 30px; flex-wrap: wrap; }\n");
        sb.append(".heading-box { background: #eef4fb; flex: 1 1 80px; border-radius: 12px; text-align: center; padding: 18px 0; color: #1a365d; font-weight: 600; }\n");
        sb.append(".heading-box strong { display: block; font-size: 22px; font-weight: 700; margin-bottom: 4px; }\n");
        sb.append(".heading-subtabs { display: flex; border: 1px solid #cbd6e2; border-radius: 10px; overflow: hidden; margin-bottom: 24px; }\n");
        sb.append(".heading-subtab { flex: 1; padding: 14px 0; background: #f9fafb; border: none; cursor: pointer; font-weight: 600; font-size: 14px; color: #555; transition: all 0.3s ease; text-align: center; }\n");
        sb.append(".heading-subtab.active { background: #fff; color: #1a365d; font-weight: 700; box-shadow: inset 0 -3px 0 #1a365d; }\n");
        sb.append(".heading-detail { background: #f9fafb; border-radius: 12px; padding: 28px 32px; font-size: 14px; color: #222; display: none; }\n");
        sb.append(".heading-detail.active { display: block; }\n");
        sb.append(".heading-detail-box { background: #fff; border: 1px solid #cbd6e2; border-radius: 10px; padding: 14px 20px; margin-bottom: 16px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05); }\n");
        sb.append(".heading-detail h4 { margin-top: 0; margin-bottom: 8px; font-weight: 700; color: #1a365d; }\n");
        sb.append(".heading-properties { display: flex; flex-wrap: wrap; gap: 16px; list-style: none; padding: 0; margin: 0; }\n");
        sb.append(".heading-properties li { display: flex; align-items: center; font-size: 14px; }\n");
        sb.append(".heading-properties li::before { content: '•'; margin-right: 8px; color: #1a365d; font-weight: 700; }\n");
        sb.append(".heading-properties li strong { font-weight: 600; margin-right: 4px; }\n");
        sb.append(".elements-overview { display: flex; gap: 20px; margin-bottom: 28px; }\n");
        sb.append(".element-box { background: #e3ebfb; color: #1a365d; font-weight: 700; font-size: 18px; flex: 1; border-radius: 12px; padding: 16px 0; text-align: center; }\n");
        sb.append(".elements-subtabs { display: flex; border: 1px solid #cbd6e2; border-radius: 10px; overflow: hidden; margin-bottom: 24px; }\n");
        sb.append(".elements-subtabs button { flex: 1; padding: 14px 0; background: #f9fafb; border: none; cursor: pointer; font-weight: 600; font-size: 14px; color: #555; transition: all 0.3s ease; }\n");
        sb.append(".elements-subtabs button.active { background: #fff; color: #1a365d; font-weight: 700; box-shadow: inset 0 -3px 0 #1a365d; }\n");
        sb.append(".element-details { background: #f9fafb; border-radius: 12px; padding: 28px 32px; font-size: 14px; color: #222; }\n");
        sb.append(".element-header { font-weight: 600; font-size: 16px; margin-bottom: 20px; color: #222222; }\n");
        sb.append("details { border: 1px solid #cbd6e2; border-radius: 10px; padding: 14px 20px; margin-bottom: 16px; background: #fff; }\n");
        sb.append("summary { font-weight: 600; font-size: 15px; outline: none; user-select: none; list-style: none; position: relative; cursor: default; }\n");
        sb.append("summary::-webkit-details-marker { display: none; }\n");
        sb.append("summary::after { content: \"▼\"; position: absolute; right: 20px; top: 50%; transform: translateY(-50%); font-size: 13px; color: #666; cursor: pointer; }\n");
        sb.append("details[open] summary::after { content: \"▲\"; }\n");
        sb.append(".element-row { display: flex; gap: 8px; margin-top: 18px; flex-wrap: wrap; }\n");
        sb.append(".element-row > div { flex: 1; min-width: 120px; margin-bottom: 12px; display: block; }\n");
        sb.append(".element-row > div strong { font-weight: 700; color: #1a365d; display: block; margin-bottom: 4px; }\n");
        sb.append(".element-row > div span.value { font-weight: 400; color: #222; display: block; }\n");
        sb.append(".element-url-row { margin-top: 18px; display: flex; justify-content: space-between; }\n");
        sb.append(".color-box { display: inline-block; width: 18px; height: 18px; border-radius: 5px; border: 1px solid #ccc; vertical-align: middle; margin-right: 4px; }\n");
        sb.append(".button-color-box { width: 20px; height: 20px; margin-right: 6px; border-radius: 4px; padding: 4px 8px; }\n");
        sb.append(".link-url { color: #006; font-weight: normal; }\n");
        sb.append(".link-url:hover { text-decoration: underline; }\n");
        sb.append(".color-pair-row { display: flex; align-items: center; gap: 20px; }\n");
        sb.append(".color-pair-row > div { display: flex; flex-direction: column; }\n");
        sb.append(".color-pair-row .color-box { margin-top: 4px; }\n");
        sb.append(".paragraph-card { background: #fff; border: 1px solid #cbd6e2; border-radius: 10px; padding: 25px; margin-bottom: 16px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05); width: 100%; box-sizing: border-box; }\n");
        sb.append(".paragraph-card textarea { width: 100%; max-width: calc(100% - 50px); height: 100px; font-size: 14px; font-family: 'Roboto', sans-serif; resize: vertical; padding: 10px; border: 1px solid #ccc; border-radius: 4px; color: #444; margin-bottom: 15px; overflow-y: auto; box-sizing: border-box; }\n");
        sb.append(".paragraph-card .details { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; }\n");
        sb.append(".paragraph-card .details div { padding: 10px; background: #f9fafb; border-radius: 4px; }\n");
        sb.append(".paragraph-card .details div strong { display: block; margin-bottom: 5px; color: #1a365d; }\n");
        sb.append(".paragraph-card .details div span { color: #222; }\n");
        sb.append(".issues-overview { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 30px; }\n");
        sb.append(".issue-box { flex: 1 1 180px; text-align: center; font-weight: 700; font-size: 18px; border-radius: 12px; padding: 24px 20px; }\n");
        sb.append(".issue-box.total { background: #eee; color: #333; }\n");
        sb.append(".issue-box.grammar { background: #ffe4c6; color: #c7570e; }\n");
        sb.append(".issue-box.broken { background: #fdd9d9; color: #ba1515; }\n");
        sb.append(".issues-subtabs { display: flex; border: 1px solid #cbd6e2; border-radius: 10px; overflow: hidden; max-width: 360px; margin-bottom: 20px; }\n");
        sb.append(".issues-subtabs button { flex: 1; padding: 14px 0; background: #f9fafb; border: none; cursor: pointer; font-weight: 600; font-size: 14px; color: #e07a5f; transition: all 0.3s ease; }\n");
        sb.append(".issues-subtabs button.active { background: #fff; border: 1px solid #e07a5f; border-bottom: none; color: #c7570e; font-weight: 700; }\n");
        sb.append(".issue-section { border-radius: 12px; padding: 28px 32px; font-size: 14px; color: #a15319; border: 1px solid #e07a5f; background: #fff0d9; }\n");
        sb.append(".broken-issue-section { border-color: #ba1515; background: #ffe6e6; color: #9e1212; }\n");
        sb.append(".issue-section strong { display: block; margin-bottom: 8px; font-weight: 700; color: inherit; }\n");
        sb.append(".issue-section textarea, .issue-section input[type=\"text\"] { width: 100%; padding: 10px; border-radius: 6px; border: 1px solid #cbd6e2; font-family: 'Roboto', sans-serif; font-size: 14px; resize: vertical; margin-bottom: 16px; color: #222; }\n");
        sb.append("table { width: 100%; border-collapse: collapse; font-size: 14px; }\n");
        sb.append("thead tr { background: #fdd9d9; color: #ba1515; font-weight: 700; }\n");
        sb.append("th, td { padding: 12px 14px; border: 1px solid #f4a29a; text-align: left; }\n");
        sb.append("a.link-table { color: #3a6fd1; text-decoration: none; }\n");
        sb.append("a.link-table:hover { text-decoration: underline; }\n");
        sb.append("td.status { color: #ba1515; font-weight: 700; }\n");
        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<div class=\"dashboard-container\">\n");

        sb.append("<header aria-label=\"WebQA Dashboard header\">");
        sb.append("<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\" focusable=\"false\"><path d=\"M3 13h2v-2H3v2zm3 0h2v-2H6v2zm3 0h2v-2h-2v2zM3 18h2v-2H3v2zm3 0h2v-2H6v2zm3 0h2v-2h-2v2zM3 8h2V6H3v2zm3 0h2V6H6v2zm3 0h2V6h-2v2zm8 2v7a2 2 0 11-4 0v-7h-2v7a4 4 0 008 0v-7h-2zm0-6v2h2V4h-2zm0 8v-2h2v2h--2z\"/></svg>\n");
        sb.append("WebQA Dashboard\n");
        sb.append("<span>Analyze website structure and SEO</span>\n");
        sb.append("</header>\n");

        WebsiteData data = websiteDataList.get(websiteDataList.size() - 1);

        sb.append("<div class=\"analyze-bar\">");
        sb.append("<input type=\"url\" placeholder=\"Enter URL to analyze\" value=\"").append(escapeHtml(data.url))
                .append("\" aria-label=\"Enter URL to analyze\" readonly />\n");
        sb.append("<button aria-label=\"Analyze website\">Analyze</button>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"dashboard-section\">");
        sb.append("<div class=\"dashboard-header\" id=\"dashboard-title\">Dashboard for: ").append(escapeHtml(data.url))
                .append("</div>\n");
        sb.append("<div class=\"dashboard-subheader\">Analyzed website structure, content, and SEO elements</div>\n");
        sb.append("</div>\n");

        int headingCount = data.headers.values().stream().mapToInt(List::size).sum();
        List<ButtonOrLink> buttonElements = data.buttons.stream().filter(b -> "button".equalsIgnoreCase(b.type))
                .collect(Collectors.toList());
        List<ButtonOrLink> linkElements = data.buttons.stream()
                .filter(b -> "a".equalsIgnoreCase(b.type) && b.href != null && !b.href.isEmpty())
                .collect(Collectors.toList());
        int elementsCount = buttonElements.size() + linkElements.size() + data.paragraphs.size();
        int issuesCount = data.grammarIssues.size() + data.brokenLinks.size();

        sb.append("<div class=\"tabs\" role=\"tablist\" aria-label=\"Dashboard Sections Tabs\">");
        sb.append("<div class=\"tab active\" role=\"tab\" tabindex=\"0\" aria-selected=\"true\" aria-controls=\"seo-info\" id=\"tab-seo-info\" data-tab=\"seo-info\">SEO Information</div>\n");
        sb.append("<div class=\"tab\" role=\"tab\" tabindex=\"-1\" aria-selected=\"false\" aria-controls=\"headings\" id=\"tab-headings\" data-tab=\"headings\">Headings (")
                .append(headingCount).append(")</div>\n");
        sb.append("<div class=\"tab\" role=\"tab\" tabindex=\"-1\" aria-selected=\"false\" aria-controls=\"elements\" id=\"tab-elements\" data-tab=\"elements\">Elements (")
                .append(elementsCount).append(")</div>\n");
        sb.append("<div class=\"tab\" role=\"tab\" tabindex=\"-1\" aria-selected=\"false\" aria-controls=\"issues\" id=\"tab-issues\" data-tab=\"issues\">Issues (")
                .append(issuesCount).append(")</div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"tab-content active\" id=\"seo-info\" role=\"tabpanel\" tabindex=\"0\" aria-labelledby=\"tab-seo-info\">\n");
        sb.append("<div class=\"seo-grid\" aria-label=\"URL and SEO Information\">\n");

        sb.append("<div class=\"seo-card\">\n");
        sb.append("<h3>URL</h3>\n");
        sb.append("<p>").append(escapeHtml(data.url)).append("</p>\n");
        sb.append("</div>\n");

        boolean pageTitleValid = data.title != null && !data.title.trim().isEmpty()
                && !data.title.equals("No data found") && data.title.length() <= 60;
        sb.append("<div class=\"seo-card\">\n");
        sb.append("<h3>Page Title</h3>\n");
        sb.append("<p>").append(escapeHtml(data.title));
        if (pageTitleValid) {
            sb.append(" <span class=\"ok\" aria-label=\"Page title valid\">✔</span>");
        }
        sb.append("</p>\n");
        sb.append("</div>\n");

        boolean metaDescriptionTooLong = !data.metaDescription.equals("No data found")
                && data.metaDescription.length() > 160;
        sb.append("<div class=\"seo-card\">\n");
        sb.append("<h3>Meta Description</h3>\n");
        sb.append("<p>").append(escapeHtml(data.metaDescription));
        if (metaDescriptionTooLong) {
            sb.append("<span class=\"warning\" aria-label=\"Meta description too long\">Description exceeds recommended length (160 characters)</span>");
        }
        sb.append("</p>\n");
        sb.append("</div>\n");

        sb.append("</div>\n");

        int seoScore = calculateSeoScore(data);
        String[] seoDetails = generateSeoScoreDetails(data).split("\\|");
        sb.append("<div class=\"seo-score-container\" aria-label=\"SEO Score\">\n");
        sb.append("<div class=\"score-circle\" style=\"background: conic-gradient(#1a365d ").append(seoScore)
                .append("%, #e3e9f7 0);\">").append(seoScore).append(" <span>/ 100</span></div>\n");
        sb.append("<div class=\"score-metrics\">\n");
        sb.append("<div class=\"score-box score-headings\">Headings Structure<br><small>").append(seoDetails[0])
                .append("</small></div>\n");
        sb.append("<div class=\"score-box score-content\">Content Quality<br><small>").append(seoDetails[1])
                .append("</small></div>\n");
        sb.append("<div class=\"score-box score-link\">Link Health<br><small>").append(seoDetails[2])
                .append("</small></div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"tab-content\" id=\"headings\" role=\"tabpanel\" tabindex=\"-1\" aria-labelledby=\"tab-headings\">\n");
        sb.append("<div class=\"heading-structure\" aria-label=\"Headings Structure Summary\">\n");
        for (int i = 1; i <= 6; i++) {
            String tag = "h" + i;
            int count = data.headers.getOrDefault(tag, Collections.emptyList()).size();
            sb.append("<div class=\"heading-box\"").append(count == 0 ? " style=\"opacity:0.4\"" : "")
                    .append("><strong>").append(count).append("</strong>").append(tag.toUpperCase()).append("</div>\n");
        }
        sb.append("</div>\n");

        sb.append("<div class=\"heading-subtabs\" role=\"tablist\" aria-label=\"Heading Levels Tabs\">\n");
        for (int i = 1; i <= 6; i++) {
            String tag = "h" + i;
            sb.append("<div class=\"heading-subtab").append(i == 1 ? " active" : "")
                    .append("\" role=\"tab\" tabindex=\"").append(i == 1 ? "0" : "-1").append("\" aria-selected=\"")
                    .append(i == 1 ? "true" : "false").append("\" aria-controls=\"heading-").append(tag)
                    .append("\" id=\"subtab-heading-").append(tag).append("\" data-tab=\"heading-").append(tag)
                    .append("\">").append(tag.toUpperCase()).append("</div>\n");
        }
        sb.append("</div>\n");

        for (int i = 1; i <= 6; i++) {
            String tag = "h" + i;
            List<HeadingElement> headings = data.headers.getOrDefault(tag, Collections.emptyList());
            sb.append("<div class=\"heading-detail").append(i == 1 ? " active" : "").append("\" id=\"heading-")
                    .append(tag).append("\" role=\"tabpanel\" tabindex=\"").append(i == 1 ? "0" : "-1")
                    .append("\" aria-labelledby=\"subtab-heading-").append(tag).append("\">\n");
            if (headings.isEmpty()) {
                sb.append("<p><strong>No ").append(tag.toUpperCase()).append(" headings found.</strong></p>\n");
            } else {
                for (HeadingElement h : headings) {
                    sb.append("<div class=\"heading-detail-box\">\n");
                    sb.append("<h4>").append(tag.toUpperCase()).append(": ").append(escapeHtml(h.text))
                            .append("</h4>\n");
                    sb.append("<ul class=\"heading-properties\">\n");
                    sb.append("<li><strong>Font Family:</strong> ").append(escapeHtml(h.fontFamily)).append("</li>\n");
                    sb.append("<li><strong>Font Size:</strong> ").append(escapeHtml(h.fontSize)).append("</li>\n");
                    sb.append("<li><strong>Font Weight:</strong> ").append(escapeHtml(h.fontWeight)).append(" ")
                            .append(escapeHtml(FONT_WEIGHT_NAMES.getOrDefault(h.fontWeight, ""))).append("</li>\n");
                    sb.append("<li><strong>Line Height:</strong> ").append(escapeHtml(h.lineHeight)).append("</li>\n");
                    sb.append("<li><strong>Letter Spacing:</strong> ").append(escapeHtml(h.letterSpacing))
                            .append("</li>\n");
                    sb.append("<li><strong>Color:</strong> <span class=\"color-box\" style=\"background:")
                            .append(escapeHtml(cssColorToHex(h.color))).append("\"></span> ")
                            .append(escapeHtml(cssColorToHex(h.color))).append("</li>\n");
                    sb.append("</ul>\n");
                    sb.append("</div>\n");
                }
            }
            sb.append("</div>\n");
        }
        sb.append("</div>\n");

        sb.append("<div class=\"tab-content\" id=\"elements\" role=\"tabpanel\" tabindex=\"-1\" aria-labelledby=\"tab-elements\">\n");
        sb.append("<div class=\"elements-overview\" aria-label=\"Elements Overview\">\n");
        sb.append("<div class=\"element-box\">").append(buttonElements.size()).append(" BUTTONS</div>\n");
        sb.append("<div class=\"element-box\">").append(linkElements.size()).append(" LINKS</div>\n");
        sb.append("<div class=\"element-box\">").append(data.paragraphs.size()).append(" PARAGRAPHS</div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"elements-subtabs\" role=\"tablist\" aria-label=\"Elements sub tabs\">\n");
        sb.append("<button class=\"elements-tab active\" role=\"tab\" aria-selected=\"true\" tabindex=\"0\" data-tab=\"buttons\">Buttons</button>\n");
        sb.append("<button class=\"elements-tab\" role=\"tab\" aria-selected=\"false\" tabindex=\"-1\" data-tab=\"links\">Links</button>\n");
        sb.append("<button class=\"elements-tab\" role=\"tab\" aria-selected=\"false\" tabindex=\"-1\" data-tab=\"paragraphs\">Paragraphs</button>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"element-details\" id=\"buttons\" role=\"tabpanel\" tabindex=\"0\">\n");
        sb.append("<div class=\"element-header\">Button Elements</div>\n");
        if (buttonElements.isEmpty()) {
            sb.append("<p>No buttons found.</p>\n");
        } else {
            for (ButtonOrLink b : buttonElements) {
                sb.append("<details open>\n");
                sb.append("<summary>").append(escapeHtml(b.text)).append("</summary>\n");
                sb.append("<div>\n");
                sb.append("<div class=\"element-row\">\n");
                if (!b.ariaLabel.equals("Not available")) {
                    sb.append("<div><strong>ARIA Label:</strong> <span class=\"value\">")
                            .append(escapeHtml(b.ariaLabel)).append("</span></div>\n");
                }
                if (!b.fontFamily.equals("Not available")) {
                    sb.append("<div><strong>Font Family:</strong> <span class=\"value\">")
                            .append(escapeHtml(b.fontFamily)).append("</span></div>\n");
                }
                if (!b.fontSize.equals("Not available")) {
                    sb.append("<div><strong>Font Size:</strong> <span class=\"value\">").append(escapeHtml(b.fontSize))
                            .append("</span></div>\n");
                }
                if (!b.fontWeight.equals("Not available")) {
                    sb.append("<div><strong>Font Weight:</strong> <span class=\"value\">")
                            .append(escapeHtml(b.fontWeight)).append(" ")
                            .append(escapeHtml(FONT_WEIGHT_NAMES.getOrDefault(b.fontWeight, "")))
                            .append("</span></div>\n");
                }
                if (!b.lineHeight.equals("Not available")) {
                    sb.append("<div><strong>Line Height:</strong> <span class=\"value\">")
                            .append(escapeHtml(b.lineHeight)).append("</span></div>\n");
                }
                if (!b.letterSpacing.equals("Not available")) {
                    sb.append("<div><strong>Letter Spacing:</strong> <span class=\"value\">")
                            .append(escapeHtml(b.letterSpacing)).append("</span></div>\n");
                }
                if (!b.padding.equals("Not available")) {
                    sb.append("<div><strong>Padding:</strong> <span class=\"value\">").append(escapeHtml(b.padding))
                            .append("</span></div>\n");
                }
                if (!b.borderRadius.equals("Not available")) {
                    sb.append("<div><strong>Border Radius:</strong> <span class=\"value\">")
                            .append(escapeHtml(b.borderRadius)).append("</span></div>\n");
                }
                sb.append("</div>\n");
                sb.append("<div class=\"color-pair-row\">\n");
                if (!b.buttonColor.equals("Not available")) {
                    sb.append("<div><strong>Background Color:</strong> <span class=\"value\"><span class=\"color-box button-color-box\" style=\"background: ")
                            .append(escapeHtml(cssColorToHex(b.buttonColor))).append(";\"></span> ")
                            .append(escapeHtml(cssColorToHex(b.buttonColor))).append("</span></div>\n");
                } else {
                    sb.append("<div><strong>Background Color:</strong> <span class=\"value\"><span class=\"color-box button-color-box\" style=\"background: #003c69;\"></span> #003c69</span></div>\n");
                }
                if (!b.textColor.equals("Not available")) {
                    sb.append("<div><strong>Text Color:</strong> <span class=\"value\"><span class=\"color-box button-color-box\" style=\"background: ")
                            .append(escapeHtml(cssColorToHex(b.textColor))).append(";\"></span> ")
                            .append(escapeHtml(cssColorToHex(b.textColor))).append("</span></div>\n");
                } else {
                    sb.append("<div><strong>Text Color:</strong> <span class=\"value\"><span class=\"color-box button-color-box\" style=\"background: #ffffff;\"></span> #ffffff</span></div>\n");
                }
                sb.append("</div>\n");
                sb.append("<div class=\"element-url-row\">\n");
                sb.append("<div><strong>URL:</strong> <span class=\"value\">");
                if (b.href != null && !b.href.isEmpty() && !b.href.equals("Not available")) {
                    sb.append("<a href=\"").append(escapeHtml(b.href))
                            .append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"link-url\">")
                            .append(escapeHtml(b.href)).append("</a>");
                } else {
                    sb.append("No URL associated");
                }
                sb.append("</span></div>\n");
                sb.append("</div>\n");
                sb.append("</div>\n");
                sb.append("</details>\n");
            }
        }
        sb.append("</div>\n");

        sb.append("<div class=\"element-details\" id=\"links\" role=\"tabpanel\" tabindex=\"0\" hidden>\n");
        sb.append("<div class=\"element-header\">Link Elements</div>\n");
        if (linkElements.isEmpty()) {
            sb.append("<p>No links found.</p>\n");
        } else {
            for (ButtonOrLink l : linkElements) {
                sb.append("<details open>\n");
                sb.append("<summary>").append(escapeHtml(l.text)).append("</summary>\n");
                sb.append("<div>\n");
                sb.append("<div class=\"element-row\">\n");
                if (!l.fontFamily.equals("Not available")) {
                    sb.append("<div><strong>Font Family:</strong> <span class=\"value\">")
                            .append(escapeHtml(l.fontFamily)).append("</span></div>\n");
                }
                if (!l.fontSize.equals("Not available")) {
                    sb.append("<div><strong>Font Size:</strong> <span class=\"value\">").append(escapeHtml(l.fontSize))
                            .append("</span></div>\n");
                }
                if (!l.fontWeight.equals("Not available")) {
                    sb.append("<div><strong>Font Weight:</strong> <span class=\"value\">")
                            .append(escapeHtml(l.fontWeight)).append(" ")
                            .append(escapeHtml(FONT_WEIGHT_NAMES.getOrDefault(l.fontWeight, "")))
                            .append("</span></div>\n");
                }
                if (!l.lineHeight.equals("Not available")) {
                    sb.append("<div><strong>Line Height:</strong> <span class=\"value\">")
                            .append(escapeHtml(l.lineHeight)).append("</span></div>\n");
                }
                if (!l.letterSpacing.equals("Not available")) {
                    sb.append("<div><strong>Letter Spacing:</strong> <span class=\"value\">")
                            .append(escapeHtml(l.letterSpacing)).append("</span></div>\n");
                }
                if (!l.buttonColor.equals("Not available")) {
                    sb.append("<div><strong>Background Color:</strong> <span class=\"value\"><span class=\"color-box button-color-box\" style=\"background: ")
                            .append(escapeHtml(cssColorToHex(l.buttonColor))).append(";\"></span> ")
                            .append(escapeHtml(cssColorToHex(l.buttonColor))).append("</span></div>\n");
                }
                if (!l.textColor.equals("Not available")) {
                    sb.append("<div><strong>Text Color:</strong> <span class=\"value\"><span class=\"color-box button-color-box\" style=\"background: ")
                            .append(escapeHtml(cssColorToHex(l.textColor))).append(";\"></span> ")
                            .append(escapeHtml(cssColorToHex(l.textColor))).append("</span></div>\n");
                }
                sb.append("</div>\n");
                sb.append("<div class=\"element-url-row\">\n");
                sb.append("<div><strong>URL:</strong> <span class=\"value\"><a href=\"").append(escapeHtml(l.href))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"link-url\">")
                        .append(escapeHtml(l.href)).append("</a></span></div>\n");
                sb.append("</div>\n");
                sb.append("</div>\n");
                sb.append("</details>\n");
            }
        }
        sb.append("</div>\n");

        sb.append("<div class=\"element-details\" id=\"paragraphs\" role=\"tabpanel\" tabindex=\"0\" hidden>\n");
        sb.append("<div class=\"element-header\">Paragraph Elements</div>\n");
        if (data.paragraphs.isEmpty()) {
            sb.append("<p>No paragraphs found.</p>\n");
        } else {
            for (ParagraphStyle p : data.paragraphs) {
                sb.append("<div class=\"paragraph-card\">\n");
                sb.append("<textarea readonly>").append(escapeHtml(p.text)).append("</textarea>\n");
                sb.append("<div class=\"details\">\n");
                sb.append("<div><strong>Font Family:</strong> <span>").append(escapeHtml(p.fontFamily))
                        .append("</span></div>\n");
                sb.append("<div><strong>Font Size:</strong> <span>").append(escapeHtml(p.fontSize))
                        .append("</span></div>\n");
                sb.append("<div><strong>Font Weight:</strong> <span>").append(escapeHtml(p.fontWeight)).append(" ")
                        .append(escapeHtml(FONT_WEIGHT_NAMES.getOrDefault(p.fontWeight, ""))).append("</span></div>\n");
                sb.append("<div><strong>Line Height:</strong> <span>").append(escapeHtml(p.lineHeight))
                        .append("</span></div>\n");
                sb.append("<div><strong>Letter Spacing:</strong> <span>").append(escapeHtml(p.letterSpacing))
                        .append("</span></div>\n");
                sb.append("<div><strong>Color:</strong> <span><span class=\"color-box\" style=\"background: ")
                        .append(escapeHtml(cssColorToHex(p.color))).append("\"></span> ")
                        .append(escapeHtml(cssColorToHex(p.color))).append("</span></div>\n");
                sb.append("</div>\n");
                sb.append("</div>\n");
            }
        }
        sb.append("</div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"tab-content\" id=\"issues\" role=\"tabpanel\" tabindex=\"-1\" aria-labelledby=\"tab-issues\">\n");
        sb.append("<div class=\"issues-overview\" aria-label=\"Issues Overview\">\n");
        sb.append("<div class=\"issue-box total\">").append(issuesCount).append(" TOTAL ISSUES</div>\n");
        sb.append("<div class=\"issue-box grammar\">").append(data.grammarIssues.size())
                .append(" SPELLING ISSUES</div>\n");
        sb.append("<div class=\"issue-box broken\">").append(data.brokenLinks.size()).append(" BROKEN LINKS</div>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"issues-subtabs\" role=\"tablist\" aria-label=\"Issues sub tabs\">\n");
        sb.append("<button class=\"issues-tab active\" role=\"tab\" aria-selected=\"true\" tabindex=\"0\" data-tab=\"grammar-issues\">Spelling Issues (")
                .append(data.grammarIssues.size()).append(")</button>\n");
        sb.append("<button class=\"issues-tab\" role=\"tab\" aria-selected=\"false\" tabindex=\"-1\" data-tab=\"broken-links\">Broken Links (")
                .append(data.brokenLinks.size()).append(")</button>\n");
        sb.append("</div>\n");

        sb.append("<div class=\"issue-section grammar-issue-section\" id=\"grammar-issues\" role=\"tabpanel\" tabindex=\"0\">\n");
        sb.append("<h3>⚠ Spelling Issues</h3>\n");
        if (data.grammarIssues.isEmpty()) {
            sb.append("<p>No spelling errors found.</p>\n");
        } else {
            for (GrammarIssue g : data.grammarIssues) {
                sb.append("<div class=\"grammar-issue-card\" style=\"background: #fff; border: 1px solid #e07a5f; border-radius: 8px; padding: 20px; margin-bottom: 16px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);\">\n");
                sb.append("<div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;\">\n");
                sb.append("<h4 style=\"margin: 0; color: #c7570e; font-size: 16px;\">").append(escapeHtml(g.elementType)).append("</h4>\n");
                sb.append("<span style=\"background: #e07a5f; color: white; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold;\">SPELLING ERROR</span>\n");
                sb.append("</div>\n");
                sb.append("<div style=\"margin-bottom: 12px;\">\n");
                sb.append("<strong style=\"color: #333; display: block; margin-bottom: 4px;\">Error Text:</strong>\n");
                sb.append("<div style=\"background: #f9f9f9; padding: 8px; border-radius: 4px; border-left: 3px solid #e07a5f; font-family: monospace;\">").append(escapeHtml(g.context)).append("</div>\n");
                sb.append("</div>\n");
                sb.append("<div style=\"margin-bottom: 12px;\">\n");
                sb.append("<strong style=\"color: #333; display: block; margin-bottom: 4px;\">Suggested Correction:</strong>\n");
                sb.append("<div style=\"background: #e8f5e8; padding: 8px; border-radius: 4px; border-left: 3px solid #4caf50; font-family: monospace;\">").append(escapeHtml(g.suggestion)).append("</div>\n");
                sb.append("</div>\n");
                sb.append("<div style=\"margin-bottom: 12px;\">\n");
                sb.append("<strong style=\"color: #333; display: block; margin-bottom: 4px;\">Issue Description:</strong>\n");
                sb.append("<div style=\"background: #f0f8ff; padding: 8px; border-radius: 4px; border-left: 3px solid #2196f3; font-size: 14px;\">").append(escapeHtml(g.message)).append("</div>\n");
                sb.append("</div>\n");
                sb.append("<div>\n");
                sb.append("<strong style=\"color: #333; display: block; margin-bottom: 4px;\">Full Text Context:</strong>\n");
                sb.append("<textarea readonly rows=\"3\" style=\"width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-family: 'Roboto', sans-serif; font-size: 14px; resize: vertical;\">").append(escapeHtml(g.fullText)).append("</textarea>\n");
                sb.append("</div>\n");
                sb.append("</div>\n");
            }
        }
        sb.append("</div>\n");

        sb.append("<div class=\"issue-section broken-issue-section\" id=\"broken-links\" role=\"tabpanel\" tabindex=\"0\" hidden>\n");
        sb.append("<h3>🔗 Broken Links</h3>\n");
        sb.append("<table>\n");
        sb.append("<thead>\n<tr>\n<th>Link Text</th>\n<th>Page URL</th>\n<th>Broken URL</th>\n<th>Status</th>\n</tr>\n</thead>\n");
        sb.append("<tbody>\n");
        if (data.brokenLinks.isEmpty()) {
            sb.append("<tr><td colspan=\"4\">No broken links found.</td></tr>\n");
        } else {
            for (BrokenLink b : data.brokenLinks) {
                System.out.println("Broken link: " + b.url + ", Status: " + b.status);
                sb.append("<tr>\n");
                sb.append("<td>").append(escapeHtml(b.text)).append("</td>\n");
                sb.append("<td><a href=\"").append(escapeHtml(b.pageUrl))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"link-table\">")
                        .append(escapeHtml(b.pageUrl)).append("</a></td>\n");
                sb.append("<td><a href=\"").append(escapeHtml(b.url))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"link-table\">")
                        .append(escapeHtml(b.url)).append("</a></td>\n");
                sb.append("<td class=\"status\">").append(b.status).append("</td>\n");
                sb.append("</tr>\n");
            }
        }
        sb.append("</tbody>\n");
        sb.append("</table>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");

        sb.append("<script>\n");
        sb.append("const tabs = document.querySelectorAll('.tab');\n");
        sb.append("const tabContents = document.querySelectorAll('.tab-content');\n");
        sb.append("tabs.forEach(tab => {\n");
        sb.append("  tab.addEventListener('click', () => {\n");
        sb.append("    tabs.forEach(t => {\n");
        sb.append("      t.classList.remove('active');\n");
        sb.append("      t.setAttribute('aria-selected', 'false');\n");
        sb.append("      t.tabIndex = -1;\n");
        sb.append("    });\n");
        sb.append("    tab.classList.add('active');\n");
        sb.append("    tab.setAttribute('aria-selected', 'true');\n");
        sb.append("    tab.tabIndex = 0;\n");
        sb.append("    const target = tab.dataset.tab;\n");
        sb.append("    tabContents.forEach(content => {\n");
        sb.append("      if(content.id === target) {\n");
        sb.append("        content.classList.add('active');\n");
        sb.append("        content.setAttribute('aria-hidden', 'false');\n");
        sb.append("        content.tabIndex = 0;\n");
        sb.append("      } else {\n");
        sb.append("        content.classList.remove('active');\n");
        sb.append("        content.setAttribute('aria-hidden', 'true');\n");
        sb.append("        content.tabIndex = -1;\n");
        sb.append("      }\n");
        sb.append("    });\n");
        sb.append("  });\n");
        sb.append("});\n");

        sb.append("const headingTabs = document.querySelectorAll('.heading-subtab');\n");
        sb.append("const headingPanels = document.querySelectorAll('#headings .heading-detail');\n");
        sb.append("headingTabs.forEach(tab => {\n");
        sb.append("  tab.addEventListener('click', () => {\n");
        sb.append("    headingTabs.forEach(t => {\n");
        sb.append("      t.classList.remove('active');\n");
        sb.append("      t.setAttribute('aria-selected', 'false');\n");
        sb.append("      t.tabIndex = -1;\n");
        sb.append("    });\n");
        sb.append("    tab.classList.add('active');\n");
        sb.append("    tab.setAttribute('aria-selected', 'true');\n");
        sb.append("    tab.tabIndex = 0;\n");
        sb.append("    const target = tab.dataset.tab;\n");
        sb.append("    headingPanels.forEach(panel => {\n");
        sb.append("      if(panel.id === target) {\n");
        sb.append("        panel.classList.add('active');\n");
        sb.append("        panel.setAttribute('aria-hidden', 'false');\n");
        sb.append("        panel.tabIndex = 0;\n");
        sb.append("      } else {\n");
        sb.append("        panel.classList.remove('active');\n");
        sb.append("        panel.setAttribute('aria-hidden', 'true');\n");
        sb.append("        panel.tabIndex = -1;\n");
        sb.append("      }\n");
        sb.append("    });\n");
        sb.append("  });\n");
        sb.append("});\n");

        sb.append("const elemTabs = document.querySelectorAll('.elements-tab');\n");
        sb.append("const elemPanels = document.querySelectorAll('#elements .element-details');\n");
        sb.append("elemTabs.forEach(tab => {\n");
        sb.append("  tab.addEventListener('click', () => {\n");
        sb.append("    elemTabs.forEach(t => {\n");
        sb.append("      t.classList.remove('active');\n");
        sb.append("      t.setAttribute('aria-selected', 'false');\n");
        sb.append("      t.tabIndex = -1;\n");
        sb.append("    });\n");
        sb.append("    tab.classList.add('active');\n");
        sb.append("    tab.setAttribute('aria-selected', 'true');\n");
        sb.append("    tab.tabIndex = 0;\n");
        sb.append("    const target = tab.dataset.tab;\n");
        sb.append("    elemPanels.forEach(panel => {\n");
        sb.append("      panel.hidden = panel.id !== target;\n");
        sb.append("    });\n");
        sb.append("  });\n");
        sb.append("});\n");

        sb.append("const issueTabs = document.querySelectorAll('.issues-tab');\n");
        sb.append("const issuePanels = document.querySelectorAll('#issues .issue-section');\n");
        sb.append("issueTabs.forEach(tab => {\n");
        sb.append("  tab.addEventListener('click', () => {\n");
        sb.append("    issueTabs.forEach(t => {\n");
        sb.append("      t.classList.remove('active');\n");
        sb.append("      t.setAttribute('aria-selected', 'false');\n");
        sb.append("      t.tabIndex = -1;\n");
        sb.append("    });\n");
        sb.append("    tab.classList.add('active');\n");
        sb.append("    tab.setAttribute('aria-selected', 'true');\n");
        sb.append("    tab.tabIndex = 0;\n");
        sb.append("    const target = tab.dataset.tab;\n");
        sb.append("    issuePanels.forEach(panel => {\n");
        sb.append("      panel.hidden = panel.id !== target;\n");
        sb.append("    });\n");
        sb.append("  });\n");
        sb.append("});\n");
        sb.append("</script>\n");

        sb.append("</div>\n");
        sb.append("</body>\n</html>\n");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("Webpage_Validator_QA.html"))) {
            writer.write(sb.toString());
        }
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
        if (cssColor == null || cssColor.trim().isEmpty() || cssColor.equals("Not available"))
            return "#000000";
        if (cssColor.trim().startsWith("#"))
            return cssColor.trim();
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
            LOGGER.warning("Failed to convert CSS color to hex: " + cssColor);
            return "#000000";
        }
        return "#000000";
    }

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
		String type, text, buttonColor, textColor, fontSize, fontWeight, padding, borderRadius, lineHeight,
				letterSpacing, href, ariaLabel, fontFamily, imgAlt;
		boolean isImageLink;

		public ButtonOrLink(String type, String text, String buttonColor, String textColor, String fontSize,
				String fontWeight, String padding, String borderRadius, String lineHeight, String letterSpacing,
				String href, String ariaLabel, String fontFamily, String imgAlt, boolean isImageLink) {
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
			this.ariaLabel = ariaLabel;
			this.fontFamily = fontFamily;
			this.imgAlt = imgAlt;
			this.isImageLink = isImageLink;
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
		String context, suggestion, message, elementType, fullText;

		public GrammarIssue(String context, String suggestion, String message, String elementType, String fullText) {
			this.context = context;
			this.suggestion = suggestion;
			this.message = message;
			this.elementType = elementType;
			this.fullText = fullText;
		}
	}

	static class BrokenLink {
		String text, url, status, pageUrl;

		public BrokenLink(String text, String url, String status, String pageUrl) {
			this.text = text;
			this.url = url;
			this.status = status;
			this.pageUrl = pageUrl;
		}
	}

	public static void main(String[] args) throws Exception {
		muteLogs();
		Webpage_Validator_QA qa = new Webpage_Validator_QA();
		qa.runQAAudit("https://www.havis.com/product-category/search-by-vehicle-type-make/");
	}
}