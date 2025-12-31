package Cursor_in_Details;

import org.languagetool.JLanguageTool;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.rules.RuleMatch;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.TimeoutException;
import org.testng.annotations.*;
import org.testng.Assert;

import java.io.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.Base64;
import org.openqa.selenium.TakesScreenshot;

public class StyleGuide_ValidatorQA {

	private WebDriver driver;
	private WebDriverWait wait;
	private List<WebsiteData> websiteDataList = new ArrayList<>();
	private Map<String, WebsiteData> resolutionDataMap = new LinkedHashMap<>();
	private static final int LINK_CHECK_THREADS = 10;
	private static final Logger LOGGER = Logger.getLogger(StyleGuide_ValidatorQA.class.getName());

	// Test URL - Change this URL to test different websites
	// Based on this URL, reports will be generated for the resolutions you have
	// enabled in SCREEN_SIZES
	private static final String TEST_URL = "https://amiwebprod.wpenginepowered.com/";

	// Screen size definitions
	// To disable a screen size, simply comment out its line below
	// Note: If you comment out a resolution, also remove it from
	// RESOLUTIONS_WITHOUT_SEO if it exists there
	private static final List<ScreenSize> SCREEN_SIZES = new ArrayList<ScreenSize>() {
		{
			add(new ScreenSize("1920 × 1080", 1920, 1080, "Most popular desktop resolution"));
//        add(new ScreenSize("1440 × 900", 1440, 900, "Common medium-size screens"));
//        add(new ScreenSize("1366 × 768", 1366, 768, "Very common on budget laptops"));
//        add(new ScreenSize("1024 × 768", 1024, 768, "Standard iPad landscape"));
//		  add(new ScreenSize("768 × 1024", 768, 1024, "Standard iPad portrait"));
//        add(new ScreenSize("414 × 896", 414, 896, "Large iPhones"));
//        add(new ScreenSize("360 × 800", 360, 800, "Most Android devices"));
//        add(new ScreenSize("340 × 720", 340, 720, "Low-end or small Android screens"));
		}
	};

	// Resolutions that should NOT have SEO Information section
	// Note: Only include resolutions that are also present in SCREEN_SIZES above
	// If you comment out a resolution in SCREEN_SIZES, remove it from here as well
	private static final Set<String> RESOLUTIONS_WITHOUT_SEO = new HashSet<String>() {
		{
			add("1440 × 900");
			add("1366 × 768");
			add("1024 × 768");
			add("768 × 1024");
			add("414 × 896");
			add("360 × 800");
			add("340 × 720");
		}
	};

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

	public void runQAAuditForAllResolutions(String url) throws Exception {
		LOGGER.info("Starting QA audit for all screen resolutions...");
		for (ScreenSize screenSize : SCREEN_SIZES) {
			LOGGER.info("Analyzing at resolution: " + screenSize.name + " (" + screenSize.width + "x"
					+ screenSize.height + ")");
			try {
				runQAAudit(url, screenSize);
			} catch (Exception e) {
				LOGGER.warning("Failed to analyze resolution " + screenSize.name + ": " + e.getMessage());
				// Continue with next resolution even if one fails
			}
		}
		generateDashboardHTML();
	}

	public void runQAAudit(String url) throws Exception {
		runQAAudit(url, new ScreenSize("1920 × 1080", 1920, 1080, "Most popular desktop resolution"));
		// Generate HTML report for single resolution
		generateDashboardHTML();
	}

	public void runQAAudit(String url, ScreenSize screenSize) throws Exception {
		if (driver == null) {
			WebDriverManager.chromedriver().setup();
			ChromeOptions options = new ChromeOptions();
			options.addArguments("--disable-dev-shm-usage");
			options.addArguments("--disable-gpu");
			options.addArguments("--remote-allow-origins=*");
			options.addArguments("--headless=new");
			driver = new ChromeDriver(options);
			wait = new WebDriverWait(driver, Duration.ofSeconds(20));
		}

		// Set the window size for this resolution
		driver.manage().window().setSize(new Dimension(screenSize.width, screenSize.height));

		try {
			driver.get(url);
			wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
			wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//p | //a")));
		} catch (TimeoutException e) {
			LOGGER.warning("Failed to load page body for URL: " + url + ". Error: " + e.getMessage());
			websiteDataList.add(new WebsiteData(url, "No data found", "No data found", new HashMap<>(),
					new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "", new ArrayList<>()));
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
			LOGGER.info("Total links found: " + links.size());
			for (WebElement link : links) {
				String href = link.getAttribute("href");
				String text = link.getText();
				LOGGER.fine("Link href: " + href + ", Text: " + text);
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
			LOGGER.info("Total <p> tags found: " + allParagraphs.size());
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
			String[] textElements = { "span", "div", "label", "li", "td", "th" };
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

		// Collect all links for broken link checking (including hidden ones, as they
		// might still have 404 URLs)
		List<WebElement> validLinks = new ArrayList<>();
		try {
			List<WebElement> allLinks = driver.findElements(By.tagName("a"));
			LOGGER.info("Total links found on page: " + allLinks.size());

			for (WebElement link : allLinks) {
				try {
					String href = link.getAttribute("href");
					String linkText = link.getText() != null ? link.getText().trim() : "";

					if (href == null || href.isEmpty()) {
						LOGGER.fine("Skipping link with no href (Text: " + linkText + ")");
						continue;
					}

					// Filter out non-HTTP links but keep anchor links that might have paths
					if (href.startsWith("javascript:") || href.startsWith("mailto:") || href.startsWith("tel:")) {
						LOGGER.fine("Skipping non-HTTP link: " + href);
						continue;
					}

					// Only skip pure anchor links (#), but keep links with paths before #
					if (href.equals("#") || href.equals("javascript:void(0)")) {
						LOGGER.fine("Skipping pure anchor link: " + href);
						continue;
					}

					// Include all other links (including those with # anchors after a path)
					validLinks.add(link);
					LOGGER.fine("Added link to check: " + href + " (Text: " + linkText + ")");

				} catch (Exception e) {
					LOGGER.warning("Error processing link element: " + e.getMessage());
				}
			}
			LOGGER.info("Valid links collected for broken link check: " + validLinks.size());
		} catch (Exception e) {
			LOGGER.warning("Error filtering valid links: " + e.getMessage());
		}

		List<BrokenLink> brokenLinks = parallelBrokenLinkCheck(validLinks, exactUrl);
		LOGGER.info("Broken links detected: " + brokenLinks.size() + " broken link(s) found");

		// Extract all other HTML elements
		List<OtherElement> otherElements = extractOtherElements(driver);

		// Capture screenshot
		String screenshotBase64 = "";
		try {
			TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
			byte[] screenshotBytes = screenshotDriver.getScreenshotAs(OutputType.BYTES);
			screenshotBase64 = Base64.getEncoder().encodeToString(screenshotBytes);
			LOGGER.info("Screenshot captured successfully for resolution: " + screenSize.name);
		} catch (Exception e) {
			LOGGER.warning("Failed to capture screenshot: " + e.getMessage());
		}

		WebsiteData websiteData = new WebsiteData(exactUrl, title, metaDescription, headers, buttons, paragraphs,
				grammarIssues, brokenLinks, screenshotBase64, otherElements);

		// Capture screenshots with highlighted elements
		LOGGER.info("Starting to capture element screenshots...");
		try {
			// Step 1: Scroll through the entire page from top to bottom to trigger
			// animations
			scrollPageTopToBottom(driver);

			// Step 2: Scroll back to the top
			scrollToTop(driver);

			// Step 3: Collect ALL visible elements on the page
			List<ElementWithPosition> allElements = new ArrayList<>();
			// Track captured elements by their unique identifier to avoid duplicates across
			// scrolling
			Set<String> capturedElementIdentifiers = new HashSet<>();
			int elementIndex = 0;

			// Collect ALL actionable elements - focus on what a user would verify
			// Priority: headings, paragraphs, buttons, links, images, list items, form
			// inputs
			try {

				// Collect actionable elements - skip containers, focus on content
				// 1. Headings (h1-h6) - always capture, even if not initially visible
				for (int i = 1; i <= 6; i++) {
					try {
						List<WebElement> headings = driver.findElements(By.tagName("h" + i));
						LOGGER.info("Found " + headings.size() + " h" + i + " headings on the page");
						for (WebElement heading : headings) {
							try {
								// Try to check if element exists in DOM first
								String tagName = heading.getTagName();
								if (tagName == null || tagName.isEmpty()) {
									continue; // Element is stale or invalid
								}

								// Generate unique identifier based on properties
								String elementId = generateElementIdentifier(heading, "heading");

								// Skip if we've already captured this element
								if (capturedElementIdentifiers.contains(elementId)) {
									LOGGER.fine("Skipping duplicate heading: " + elementId);
									continue;
								}

								// Check if element is displayed, if not try to scroll to it
								boolean isVisible = heading.isDisplayed();
								if (!isVisible) {
									// Try scrolling to the element to make it visible
									try {
										scrollElementIntoView(driver, heading);
										Thread.sleep(200); // Wait for scroll
										isVisible = heading.isDisplayed();
									} catch (Exception scrollEx) {
										LOGGER.fine(
												"Could not scroll to heading, may be hidden: " + scrollEx.getMessage());
									}
								}

								// Capture heading if visible, or if it's a heading (headings are important even
								// if initially hidden)
								if (isVisible || tagName.toLowerCase().startsWith("h")) {
									Point loc = heading.getLocation();
									capturedElementIdentifiers.add(elementId);
									allElements.add(
											new ElementWithPosition(heading, "heading", loc.getY(), elementIndex++));
									LOGGER.fine("Captured heading: " + elementId);
								}
							} catch (StaleElementReferenceException e) {
								LOGGER.fine("Heading element became stale, skipping");
								continue;
							} catch (Exception e) {
								LOGGER.fine("Error processing individual heading: " + e.getMessage());
								continue;
							}
						}
					} catch (Exception e) {
						LOGGER.warning("Error collecting h" + i + " headings: " + e.getMessage());
					}
				}

				// 2. Paragraphs - capture all visible paragraphs
				try {
					List<WebElement> paragraphElements = driver.findElements(By.tagName("p"));
					for (WebElement para : paragraphElements) {
						try {
							if (!para.isDisplayed())
								continue;

							String text = para.getText();
							// Reduced minimum length from 10 to 5 to capture shorter content blocks
							if (text == null || text.trim().isEmpty() || text.trim().length() <= 5)
								continue;

							Dimension size = para.getSize();
							// Skip if too small (likely decorative)
							if (size.getWidth() < 10 || size.getHeight() < 10)
								continue;

							// Generate unique identifier
							String elementId = generateElementIdentifier(para, "paragraph");

							// Skip if already captured
							if (capturedElementIdentifiers.contains(elementId)) {
								LOGGER.fine("Skipping duplicate paragraph: " + elementId);
								continue;
							}

							Point loc = para.getLocation();
							capturedElementIdentifiers.add(elementId);
							allElements.add(new ElementWithPosition(para, "paragraph", loc.getY(), elementIndex++));
						} catch (StaleElementReferenceException e) {
							continue;
						} catch (Exception e) {
							LOGGER.fine("Error processing paragraph: " + e.getMessage());
							continue;
						}
					}
				} catch (Exception e) {
					LOGGER.warning("Error collecting paragraphs: " + e.getMessage());
				}

				// 2b. Text content in div and other container elements - capture substantial
				// text blocks
				// This ensures we capture content that's not in <p> tags (common in modern
				// websites)
				try {
					// Find all div, span, article, section, aside elements that contain text
					List<WebElement> textContainers = driver.findElements(By
							.xpath("//div | //span | //article | //section | //aside | //main | //header | //footer"));

					for (WebElement container : textContainers) {
						if (!container.isDisplayed())
							continue;

						try {
							String text = container.getText();
							if (text == null || text.trim().isEmpty() || text.trim().length() < 20) {
								continue; // Skip containers with too little text
							}

							Point loc = container.getLocation();
							Dimension size = container.getSize();

							// Skip if too small (likely decorative or icon)
							if (size.getWidth() < 50 || size.getHeight() < 20)
								continue;

							// Check if container has child elements that are already being captured
							// If it has ANY child elements (headings, paragraphs, buttons, links) that
							// we're capturing,
							// we should skip the container to avoid duplicate content
							// Only capture the container if it has NO captured child elements OR
							// if it has substantial unique direct text that's not in child elements
							boolean hasCapturedChildElements = false;
							try {
								// Check for child headings (these are always captured)
								List<WebElement> childHeadings = container
										.findElements(By.xpath(".//h1 | .//h2 | .//h3 | .//h4 | .//h5 | .//h6"));
								for (WebElement childHeading : childHeadings) {
									if (childHeading.isDisplayed()) {
										hasCapturedChildElements = true;
										break;
									}
								}

								// Check for child paragraphs (these are always captured if they have >5 chars)
								if (!hasCapturedChildElements) {
									List<WebElement> childParagraphs = container.findElements(By.tagName("p"));
									for (WebElement childPara : childParagraphs) {
										if (childPara.isDisplayed() && childPara.getText() != null
												&& !childPara.getText().trim().isEmpty()
												&& childPara.getText().trim().length() > 5) {
											hasCapturedChildElements = true;
											break;
										}
									}
								}

								// Check for child buttons (these are always captured)
								if (!hasCapturedChildElements) {
									List<WebElement> childButtons = container.findElements(By.tagName("button"));
									for (WebElement btn : childButtons) {
										if (btn.isDisplayed()) {
											hasCapturedChildElements = true;
											break;
										}
									}
								}

								// Check for child links (valid links are always captured)
								if (!hasCapturedChildElements) {
									List<WebElement> childLinks = container.findElements(By.tagName("a"));
									for (WebElement link : childLinks) {
										if (link.isDisplayed()) {
											String href = link.getAttribute("href");
											if (href != null && !href.isEmpty() && !href.startsWith("javascript:")
													&& !href.equals("#") && !href.equals("javascript:void(0)")) {
												hasCapturedChildElements = true;
												break;
											}
										}
									}
								}
							} catch (Exception e) {
								// If we can't check children, assume it might have children - be conservative
								// and skip
								hasCapturedChildElements = true;
							}

							// Only capture the container if it has NO captured child elements
							// This prevents duplicate content - if a div contains a <p> tag,
							// we capture the <p> tag, not the div wrapper
							// The container is only captured if it has substantial text content
							// but NO child elements that are already being captured
							if (!hasCapturedChildElements && text.trim().length() >= 30) {
								// Generate unique identifier
								String elementId = generateElementIdentifier(container, "paragraph");

								// Skip if already captured
								if (capturedElementIdentifiers.contains(elementId)) {
									continue;
								}

								capturedElementIdentifiers.add(elementId);
								allElements.add(
										new ElementWithPosition(container, "paragraph", loc.getY(), elementIndex++));
							}
						} catch (Exception e) {
							// Skip this container if there's an error
							continue;
						}
					}
				} catch (Exception e) {
					LOGGER.warning("Error collecting text containers: " + e.getMessage());
				}

				// 3. Buttons - capture all buttons
				try {
					List<WebElement> buttonElements = driver.findElements(By.tagName("button"));
					for (WebElement btn : buttonElements) {
						try {
							if (!btn.isDisplayed())
								continue;

							// Generate unique identifier
							String elementId = generateElementIdentifier(btn, "button");

							// Skip if already captured
							if (capturedElementIdentifiers.contains(elementId)) {
								LOGGER.fine("Skipping duplicate button: " + elementId);
								continue;
							}

							Point loc = btn.getLocation();
							capturedElementIdentifiers.add(elementId);
							allElements.add(new ElementWithPosition(btn, "button", loc.getY(), elementIndex++));
						} catch (StaleElementReferenceException e) {
							continue;
						} catch (Exception e) {
							LOGGER.fine("Error processing button: " + e.getMessage());
							continue;
						}
					}
				} catch (Exception e) {
					LOGGER.warning("Error collecting buttons: " + e.getMessage());
				}

				// 4. Links - capture all valid links (skip anchors)
				try {
					List<WebElement> links = driver.findElements(By.tagName("a"));
					for (WebElement link : links) {
						try {
							if (!link.isDisplayed())
								continue;

							String href = link.getAttribute("href");
							if (href == null || href.isEmpty() || href.startsWith("javascript:")
									|| href.startsWith("mailto:") || href.startsWith("tel:") || href.equals("#")
									|| href.equals("javascript:void(0)")) {
								continue;
							}

							// Generate unique identifier
							String elementId = generateElementIdentifier(link, "link");

							// Skip if already captured
							if (capturedElementIdentifiers.contains(elementId)) {
								LOGGER.fine("Skipping duplicate link: " + elementId);
								continue;
							}

							Point loc = link.getLocation();
							capturedElementIdentifiers.add(elementId);
							allElements.add(new ElementWithPosition(link, "link", loc.getY(), elementIndex++));
						} catch (StaleElementReferenceException e) {
							continue;
						} catch (Exception e) {
							LOGGER.fine("Error processing link: " + e.getMessage());
							continue;
						}
					}
				} catch (Exception e) {
					LOGGER.warning("Error collecting links: " + e.getMessage());
				}

				// 5. Images - capture all images
				try {
					List<WebElement> images = driver.findElements(By.tagName("img"));
					for (WebElement img : images) {
						try {
							if (!img.isDisplayed())
								continue;

							// Generate unique identifier
							String elementId = generateElementIdentifier(img, "other");

							// Skip if already captured
							if (capturedElementIdentifiers.contains(elementId)) {
								LOGGER.fine("Skipping duplicate image: " + elementId);
								continue;
							}

							Point loc = img.getLocation();
							capturedElementIdentifiers.add(elementId);
							allElements.add(new ElementWithPosition(img, "other", loc.getY(), elementIndex++));
						} catch (StaleElementReferenceException e) {
							continue;
						} catch (Exception e) {
							LOGGER.fine("Error processing image: " + e.getMessage());
							continue;
						}
					}
				} catch (Exception e) {
					LOGGER.warning("Error collecting images: " + e.getMessage());
				}

				// 6. List items (li) - ONLY capture if they don't contain actionable child
				// elements
				// Skip ALL <li> items that contain <a> or <button> - those child elements are
				// more valuable
				try {
					List<WebElement> listItems = driver.findElements(By.tagName("li"));
					for (WebElement li : listItems) {
						if (li.isDisplayed()) {
							Point loc = li.getLocation();
							Dimension size = li.getSize();
							// Skip if too small (likely decorative)
							if (size.getWidth() < 10 || size.getHeight() < 10)
								continue;

							// Check if this <li> contains ANY links or buttons - if so, skip it
							// The child elements (links/buttons) are more valuable and already captured
							boolean hasActionableChild = false;
							try {
								// Check for ANY links inside this <li>
								List<WebElement> childLinks = li.findElements(By.tagName("a"));
								for (WebElement childLink : childLinks) {
									if (childLink.isDisplayed()) {
										String href = childLink.getAttribute("href");
										// If it has a valid href, skip the <li> - the link is more valuable
										if (href != null && !href.isEmpty() && !href.startsWith("javascript:")
												&& !href.startsWith("mailto:") && !href.startsWith("tel:")
												&& !href.equals("#") && !href.equals("javascript:void(0)")) {
											hasActionableChild = true;
											break;
										}
									}
								}

								// Check for ANY buttons inside this <li>
								if (!hasActionableChild) {
									List<WebElement> childButtons = li.findElements(By.tagName("button"));
									for (WebElement childBtn : childButtons) {
										if (childBtn.isDisplayed()) {
											hasActionableChild = true;
											break;
										}
									}
								}
							} catch (Exception e) {
								// If we can't check children, skip this <li> to be safe
								continue;
							}

							// Skip <li> if it contains ANY actionable child elements (links/buttons)
							// Those child elements are already captured with full properties
							if (hasActionableChild) {
								LOGGER.fine("Skipping <li> at " + loc.getX() + "," + loc.getY()
										+ " - contains actionable child element");
								continue;
							}

							// Only capture <li> if it has direct text content AND no actionable children
							String text = li.getText();
							if (text != null && !text.trim().isEmpty()) {
								// Generate unique identifier
								String elementId = generateElementIdentifier(li, "other");

								// Skip if already captured
								if (capturedElementIdentifiers.contains(elementId)) {
									continue;
								}

								capturedElementIdentifiers.add(elementId);
								allElements.add(new ElementWithPosition(li, "other", loc.getY(), elementIndex++));
							}
						}
					}
				} catch (Exception e) {
					LOGGER.warning("Error collecting list items: " + e.getMessage());
				}

				// 7. Form inputs - capture input, textarea, select elements
				try {
					List<WebElement> inputs = driver.findElements(By.xpath("//input | //textarea | //select"));
					for (WebElement input : inputs) {
						try {
							if (!input.isDisplayed())
								continue;

							// Generate unique identifier
							String elementId = generateElementIdentifier(input, "other");

							// Skip if already captured
							if (capturedElementIdentifiers.contains(elementId)) {
								LOGGER.fine("Skipping duplicate form input: " + elementId);
								continue;
							}

							Point loc = input.getLocation();
							capturedElementIdentifiers.add(elementId);
							allElements.add(new ElementWithPosition(input, "other", loc.getY(), elementIndex++));
						} catch (StaleElementReferenceException e) {
							continue;
						} catch (Exception e) {
							LOGGER.fine("Error processing form input: " + e.getMessage());
							continue;
						}
					}
				} catch (Exception e) {
					LOGGER.warning("Error collecting form inputs: " + e.getMessage());
				}

			} catch (Exception e) {
				LOGGER.warning("Error collecting all elements: " + e.getMessage());
			}

			// Step 4: Sort all elements by Y position (top to bottom), then by X (left to
			// right)
			allElements.sort((e1, e2) -> {
				int yCompare = Integer.compare(e1.yPosition, e2.yPosition);
				if (yCompare != 0)
					return yCompare;
				// If same Y position, sort by X (left to right)
				try {
					Point loc1 = e1.element.getLocation();
					Point loc2 = e2.element.getLocation();
					return Integer.compare(loc1.getX(), loc2.getX());
				} catch (Exception ex) {
					return 0;
				}
			});

			LOGGER.info("Collected " + allElements.size() + " unique elements, now taking screenshots in order...");

			// Step 5: Take screenshots in order, scrolling to each element first
			// Use a separate Set to track which elements we've already taken screenshots of
			// This prevents the same element from being captured multiple times during
			// scrolling
			Set<String> screenshotCapturedIds = new HashSet<>();
			int actualScreenshotIndex = 0;

			for (ElementWithPosition elementWithPos : allElements) {
				try {
					WebElement element = elementWithPos.element;
					String elementType = elementWithPos.elementType;
					int index = elementWithPos.index;

					// Generate unique identifier using the same method as collection phase
					// This ensures consistency and prevents duplicates even after scrolling
					String uniqueElementId;
					try {
						uniqueElementId = generateElementIdentifier(element, elementType);
					} catch (StaleElementReferenceException e) {
						LOGGER.fine("Element became stale before screenshot, skipping");
						continue;
					} catch (Exception e) {
						LOGGER.fine("Error generating element identifier: " + e.getMessage());
						continue;
					}

					// Skip if we've already taken a screenshot of this exact element
					// This prevents duplicates when scrolling causes elements to be re-detected
					if (screenshotCapturedIds.contains(uniqueElementId)) {
						LOGGER.fine("Skipping duplicate element during screenshot: " + uniqueElementId);
						continue;
					}

					// Mark as captured before taking screenshot to prevent re-capture during scroll
					screenshotCapturedIds.add(uniqueElementId);

					// Extract properties BEFORE applying highlight (to avoid capturing highlight
					// styles)
					Map<String, String> properties = extractElementProperties(element, elementType);

					// For actionable elements, always capture - they have value even with minimal
					// properties
					// Only skip if element has absolutely no useful information
					boolean shouldCapture = true;

					if (elementType.equals("heading") || elementType.equals("paragraph")) {
						// Text elements are always valuable - capture even if properties are minimal
						shouldCapture = true;
					} else if (elementType.equals("button") || elementType.equals("link")) {
						// Buttons and links are always actionable - capture them
						shouldCapture = true;
					} else if (elementType.equals("other")) {
						// For other elements, check if they have any meaningful attributes
						// Images, list items, form inputs are all valuable
						String tagName = element.getTagName().toLowerCase();
						if (tagName.equals("img") || tagName.equals("li") || tagName.equals("input")
								|| tagName.equals("textarea") || tagName.equals("select")) {
							shouldCapture = true; // These are always valuable
						} else {
							// For other tags, check if they have meaningful properties
							shouldCapture = properties.containsKey("src") || properties.containsKey("alt")
									|| properties.containsKey("href") || properties.size() > 1; // More than just
																								// tag-name
						}
					}

					if (!shouldCapture) {
						LOGGER.fine("Skipping element " + elementType + " - no meaningful properties");
						continue;
					}

					// Scroll element into view before taking screenshot
					scrollElementIntoView(driver, element);

					// Small delay to ensure element is stable after scroll
					Thread.sleep(100);

					// Verify element is still displayed after scroll
					if (!element.isDisplayed()) {
						LOGGER.warning("Element " + elementType + " at index " + index
								+ " is not visible after scroll, skipping");
						continue;
					}

					// Capture individual screenshot for this element (highlight applied here)
					String highlightColor = getHighlightColor(actualScreenshotIndex);
					String elementScreenshotBase64 = captureScreenshotWithHighlight(driver, element, highlightColor);
					Map<String, Integer> bounds = getElementBounds(element);

					// Get text content for the element
					String text = "";
					try {
						text = element.getText();
						if (text == null || text.trim().isEmpty()) {
							text = element.getAttribute("textContent");
						}
					} catch (Exception e) {
						// Ignore if we can't get text
					}
					if (text == null || text.trim().isEmpty()) {
						text = "Not available";
					}

					String elementId = elementType + "-" + actualScreenshotIndex;
					ElementScreenshotData screenshotData = new ElementScreenshotData(elementType, elementId,
							elementScreenshotBase64, highlightColor, bounds.get("x"), bounds.get("y"),
							bounds.get("width"), bounds.get("height"), properties,
							text.length() > 100 ? text.substring(0, 100) + "..." : text, actualScreenshotIndex);
					websiteData.elementScreenshots.add(screenshotData);
					actualScreenshotIndex++;

				} catch (Exception e) {
					LOGGER.warning("Failed to capture screenshot for " + elementWithPos.elementType
							+ " element at index " + elementWithPos.index + ": " + e.getMessage());
				}
			}

			LOGGER.info("Captured " + websiteData.elementScreenshots.size() + " element screenshots in order");
		} catch (Exception e) {
			LOGGER.warning("Error during element screenshot capture: " + e.getMessage());
		}

		websiteDataList.add(websiteData);

		// Store data for this resolution
		resolutionDataMap.put(screenSize.name, websiteData);
	}

	private List<GrammarIssue> parallelGrammarCheck(List<String> textChunks) {
		LOGGER.info("Starting parallel grammar check for " + textChunks.size() + " text chunks");

		ExecutorService executor = Executors.newFixedThreadPool(4);
		List<Future<List<GrammarIssue>>> futures = new ArrayList<>();

		int processedChunks = 0;
		for (String chunk : textChunks) {
			if (chunk != null && !chunk.trim().isEmpty()) {
				processedChunks++;
				final String chunkToProcess = chunk;
				futures.add(executor.submit(() -> {
					try {
						return grammarCheck(chunkToProcess);
					} catch (Exception e) {
						LOGGER.warning("Error in grammar check for chunk: " + e.getMessage());
						return new ArrayList<GrammarIssue>();
					}
				}));
			}
		}

		LOGGER.info("Submitted " + processedChunks + " text chunks for grammar checking");

		List<GrammarIssue> allIssues = new ArrayList<>();
		int completedChecks = 0;
		for (Future<List<GrammarIssue>> f : futures) {
			try {
				List<GrammarIssue> chunkIssues = f.get();
				allIssues.addAll(chunkIssues);
				completedChecks++;
				if (chunkIssues.size() > 0) {
					LOGGER.info("Found " + chunkIssues.size() + " spelling errors in chunk " + completedChecks);
				}
			} catch (Exception e) {
				LOGGER.warning("Error during grammar check: " + e.getMessage());
				e.printStackTrace();
			}
		}

		executor.shutdown();
		try {
			executor.awaitTermination(5, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		LOGGER.info("Grammar check completed. Total spelling errors found: " + allIssues.size());
		return allIssues;
	}

	private List<BrokenLink> parallelBrokenLinkCheck(List<WebElement> links, String pageUrl)
			throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(LINK_CHECK_THREADS);
		List<Future<BrokenLink>> futures = new ArrayList<>();

		LOGGER.info("Starting broken link check for " + links.size() + " links from page: " + pageUrl);

		for (WebElement link : links) {
			try {
				String linkUrl = link.getAttribute("href");
				String linkText = link.getText() != null ? link.getText().trim() : "";

				if (linkUrl != null && !linkUrl.isEmpty()) {
					try {
						URL baseUrl = new URL(pageUrl);
						URL absoluteUrl = new URL(baseUrl, linkUrl); // Resolve relative URLs
						String resolvedUrl = absoluteUrl.toString();

						// Additional validation to ensure we're only checking legitimate links
						if (resolvedUrl.startsWith("http") && !resolvedUrl.contains("javascript:")
								&& !resolvedUrl.contains("mailto:") && !resolvedUrl.contains("tel:")) {

							// Log the link being checked for debugging (INFO level so we can see it)
							LOGGER.info("Checking link: " + resolvedUrl + " (Text: " + linkText + ")");

							final String finalResolvedUrl = resolvedUrl;
							final String finalLinkText = linkText;

							futures.add(executor.submit(() -> {
								try {
									LOGGER.info("Getting HTTP status for: " + finalResolvedUrl);
									int status = getHttpStatus(finalResolvedUrl);
									LOGGER.info("HTTP status for " + finalResolvedUrl + " = " + status);

									// Only capture 404 status codes (including soft 404s detected in getHttpStatus)
									if (status == 404) {
										LOGGER.warning("*** 404 BROKEN LINK DETECTED ***: " + finalResolvedUrl
												+ " (Link Text: " + finalLinkText + ")");
										return new BrokenLink(finalLinkText, finalResolvedUrl, String.valueOf(status),
												pageUrl);
									} else if (status != -1) {
										LOGGER.info("Link OK: " + finalResolvedUrl + " - Status: " + status);
									} else {
										LOGGER.warning("Connection failed for: " + finalResolvedUrl);
									}
									return null;
								} catch (Exception e) {
									LOGGER.warning("Exception checking link status for " + finalResolvedUrl + ": "
											+ e.getMessage());
									e.printStackTrace();
									return null;
								}
							}));
						} else {
							LOGGER.info("Skipping non-HTTP link: " + resolvedUrl);
						}
					} catch (MalformedURLException e) {
						LOGGER.warning("Invalid URL format - Base: " + pageUrl + ", Link: " + linkUrl + ", Error: "
								+ e.getMessage());
					} catch (Exception e) {
						LOGGER.warning("Error resolving URL - Link: " + linkUrl + ", Error: " + e.getMessage());
					}
				}
			} catch (Exception e) {
				LOGGER.warning("Error processing link element: " + e.getMessage());
			}
		}

		List<BrokenLink> brokenLinks = new ArrayList<>();
		int checkedCount = 0;
		int errorCount = 0;

		for (Future<BrokenLink> f : futures) {
			try {
				checkedCount++;
				BrokenLink bl = f.get();
				if (bl != null) {
					brokenLinks.add(bl);
				}
			} catch (Exception e) {
				errorCount++;
				LOGGER.warning("Error checking broken link: " + e.getMessage());
			}
		}

		executor.shutdown();
		boolean terminated = executor.awaitTermination(2, TimeUnit.MINUTES);
		if (!terminated) {
			LOGGER.warning("Broken link check timed out - some links may not have been checked");
			executor.shutdownNow();
		}

		LOGGER.info("Broken links check completed:");
		LOGGER.info("  - Links checked: " + checkedCount);
		LOGGER.info("  - Errors encountered: " + errorCount);
		LOGGER.info("  - 404 broken links detected: " + brokenLinks.size());

		if (brokenLinks.size() > 0) {
			LOGGER.warning("Found " + brokenLinks.size() + " broken link(s) with 404 status:");
			for (BrokenLink bl : brokenLinks) {
				LOGGER.warning("  - " + bl.url + " (Text: " + bl.text + ")");
			}
		}

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

	private List<OtherElement> extractOtherElements(WebDriver driver) {
		List<OtherElement> otherElements = new ArrayList<>();

		// Extract Images
		try {
			List<WebElement> images = driver.findElements(By.tagName("img"));
			for (WebElement img : images) {
				if (img.isDisplayed()) {
					String src = img.getAttribute("src") != null ? img.getAttribute("src") : "Not available";
					String alt = img.getAttribute("alt") != null ? img.getAttribute("alt") : "Not available";
					String title = img.getAttribute("title") != null ? img.getAttribute("title") : "Not available";
					String width = img.getAttribute("width") != null ? img.getAttribute("width") : "Not available";
					String height = img.getAttribute("height") != null ? img.getAttribute("height") : "Not available";
					otherElements.add(new OtherElement("img", alt.isEmpty() ? src : alt, src, alt, title, width, height,
							"", "", ""));
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting images: " + e.getMessage());
		}

		// Extract Lists (ul, ol, li)
		try {
			List<WebElement> lists = driver.findElements(By.xpath("//ul | //ol"));
			for (WebElement list : lists) {
				if (list.isDisplayed() && !list.findElements(By.tagName("li")).isEmpty()) {
					String tagName = list.getTagName();
					String text = list.getText();
					if (text != null && !text.trim().isEmpty()) {
						int itemCount = list.findElements(By.tagName("li")).size();
						otherElements.add(new OtherElement(tagName, text.substring(0, Math.min(100, text.length())), "",
								"", "", "", "", String.valueOf(itemCount), "", ""));
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting lists: " + e.getMessage());
		}

		// Extract Tables
		try {
			List<WebElement> tables = driver.findElements(By.tagName("table"));
			for (WebElement table : tables) {
				if (table.isDisplayed()) {
					int rowCount = table.findElements(By.tagName("tr")).size();
					int colCount = table.findElements(By.tagName("th")).size()
							+ table.findElements(By.tagName("td")).size();
					String caption = "";
					try {
						WebElement captionEl = table.findElement(By.tagName("caption"));
						caption = captionEl.getText();
					} catch (Exception ex) {
						// No caption
					}
					otherElements.add(new OtherElement("table", caption.isEmpty() ? "Table" : caption, "", "", "", "",
							"", String.valueOf(rowCount), String.valueOf(colCount), ""));
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting tables: " + e.getMessage());
		}

		// Extract Forms and Inputs
		try {
			List<WebElement> forms = driver.findElements(By.tagName("form"));
			for (WebElement form : forms) {
				if (form.isDisplayed()) {
					String action = form.getAttribute("action") != null ? form.getAttribute("action") : "Not available";
					String method = form.getAttribute("method") != null ? form.getAttribute("method") : "GET";
					int inputCount = form.findElements(By.tagName("input")).size();
					int textareaCount = form.findElements(By.tagName("textarea")).size();
					int selectCount = form.findElements(By.tagName("select")).size();
					otherElements.add(new OtherElement("form", "Form", action, method, "", "", "",
							String.valueOf(inputCount + textareaCount + selectCount), "", ""));
				}
			}

			// Extract standalone inputs (not inside forms)
			List<WebElement> inputs = driver.findElements(By.tagName("input"));
			for (WebElement input : inputs) {
				if (input.isDisplayed()) {
					try {
						input.findElement(By.xpath("./ancestor::form"));
						// If we get here, input is inside a form, skip it
						continue;
					} catch (NoSuchElementException ex) {
						// Input is not inside a form, add it
						String type = input.getAttribute("type") != null ? input.getAttribute("type") : "text";
						String name = input.getAttribute("name") != null ? input.getAttribute("name") : "Not available";
						String placeholder = input.getAttribute("placeholder") != null
								? input.getAttribute("placeholder")
								: "Not available";
						otherElements.add(new OtherElement("input", name, type, placeholder, "", "", "", "", "", ""));
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting forms/inputs: " + e.getMessage());
		}

		// Extract Textareas
		try {
			List<WebElement> textareas = driver.findElements(By.tagName("textarea"));
			for (WebElement textarea : textareas) {
				if (textarea.isDisplayed()) {
					String name = textarea.getAttribute("name") != null ? textarea.getAttribute("name")
							: "Not available";
					String placeholder = textarea.getAttribute("placeholder") != null
							? textarea.getAttribute("placeholder")
							: "Not available";
					String rows = textarea.getAttribute("rows") != null ? textarea.getAttribute("rows")
							: "Not available";
					String cols = textarea.getAttribute("cols") != null ? textarea.getAttribute("cols")
							: "Not available";
					otherElements.add(new OtherElement("textarea", name, placeholder, rows, cols, "", "", "", "", ""));
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting textareas: " + e.getMessage());
		}

		// Extract Selects and Options
		try {
			List<WebElement> selects = driver.findElements(By.tagName("select"));
			for (WebElement select : selects) {
				if (select.isDisplayed()) {
					String name = select.getAttribute("name") != null ? select.getAttribute("name") : "Not available";
					int optionCount = select.findElements(By.tagName("option")).size();
					otherElements.add(
							new OtherElement("select", name, "", "", "", "", "", String.valueOf(optionCount), "", ""));
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting selects: " + e.getMessage());
		}

		// Extract Semantic Elements (section, article, aside, nav, header, footer,
		// main)
		String[] semanticTags = { "section", "article", "aside", "nav", "header", "footer", "main" };
		for (String tag : semanticTags) {
			try {
				List<WebElement> elements = driver.findElements(By.tagName(tag));
				for (WebElement el : elements) {
					if (el.isDisplayed()) {
						String text = el.getText();
						String id = el.getAttribute("id") != null ? el.getAttribute("id") : "Not available";
						String className = el.getAttribute("class") != null ? el.getAttribute("class")
								: "Not available";
						if (text != null && !text.trim().isEmpty()) {
							otherElements.add(new OtherElement(tag, text.substring(0, Math.min(100, text.length())), id,
									className, "", "", "", "", "", ""));
						} else {
							otherElements.add(new OtherElement(tag, id, className, "", "", "", "", "", "", ""));
						}
					}
				}
			} catch (Exception e) {
				LOGGER.warning("Error extracting " + tag + " elements: " + e.getMessage());
			}
		}

		// Extract Media Elements (video, audio, iframe, embed, object)
		String[] mediaTags = { "video", "audio", "iframe", "embed", "object" };
		for (String tag : mediaTags) {
			try {
				List<WebElement> elements = driver.findElements(By.tagName(tag));
				for (WebElement el : elements) {
					if (el.isDisplayed()) {
						String src = el.getAttribute("src") != null ? el.getAttribute("src") : "Not available";
						String width = el.getAttribute("width") != null ? el.getAttribute("width") : "Not available";
						String height = el.getAttribute("height") != null ? el.getAttribute("height") : "Not available";
						otherElements.add(new OtherElement(tag, src, width, height, "", "", "", "", "", ""));
					}
				}
			} catch (Exception e) {
				LOGGER.warning("Error extracting " + tag + " elements: " + e.getMessage());
			}
		}

		// Extract Other Semantic Elements (figure, figcaption, blockquote, cite, code,
		// pre)
		String[] otherSemanticTags = { "figure", "figcaption", "blockquote", "cite", "code", "pre" };
		for (String tag : otherSemanticTags) {
			try {
				List<WebElement> elements = driver.findElements(By.tagName(tag));
				for (WebElement el : elements) {
					if (el.isDisplayed()) {
						String text = el.getText();
						if (text != null && !text.trim().isEmpty()) {
							otherElements.add(new OtherElement(tag, text.substring(0, Math.min(200, text.length())), "",
									"", "", "", "", "", "", ""));
						}
					}
				}
			} catch (Exception e) {
				LOGGER.warning("Error extracting " + tag + " elements: " + e.getMessage());
			}
		}

		// Extract Labels
		try {
			List<WebElement> labels = driver.findElements(By.tagName("label"));
			for (WebElement label : labels) {
				if (label.isDisplayed()) {
					String text = label.getText();
					String forAttr = label.getAttribute("for") != null ? label.getAttribute("for") : "Not available";
					if (text != null && !text.trim().isEmpty()) {
						otherElements.add(new OtherElement("label", text, forAttr, "", "", "", "", "", "", ""));
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting labels: " + e.getMessage());
		}

		// Extract SVG elements
		try {
			List<WebElement> svgs = driver.findElements(By.tagName("svg"));
			for (WebElement svg : svgs) {
				if (svg.isDisplayed()) {
					String width = svg.getAttribute("width") != null ? svg.getAttribute("width") : "Not available";
					String height = svg.getAttribute("height") != null ? svg.getAttribute("height") : "Not available";
					String viewBox = svg.getAttribute("viewBox") != null ? svg.getAttribute("viewBox")
							: "Not available";
					otherElements
							.add(new OtherElement("svg", "SVG Element", width, height, viewBox, "", "", "", "", ""));
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting SVG elements: " + e.getMessage());
		}

		// Extract Details/Summary
		try {
			List<WebElement> details = driver.findElements(By.tagName("details"));
			for (WebElement detail : details) {
				if (detail.isDisplayed()) {
					String text = detail.getText();
					boolean isOpen = detail.getAttribute("open") != null;
					if (text != null && !text.trim().isEmpty()) {
						otherElements.add(new OtherElement("details", text.substring(0, Math.min(100, text.length())),
								isOpen ? "open" : "closed", "", "", "", "", "", "", ""));
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting details elements: " + e.getMessage());
		}

		// Extract Progress and Meter
		try {
			List<WebElement> progress = driver.findElements(By.tagName("progress"));
			for (WebElement el : progress) {
				if (el.isDisplayed()) {
					String value = el.getAttribute("value") != null ? el.getAttribute("value") : "Not available";
					String max = el.getAttribute("max") != null ? el.getAttribute("max") : "Not available";
					otherElements.add(new OtherElement("progress", "Progress", value, max, "", "", "", "", "", ""));
				}
			}

			List<WebElement> meters = driver.findElements(By.tagName("meter"));
			for (WebElement el : meters) {
				if (el.isDisplayed()) {
					String value = el.getAttribute("value") != null ? el.getAttribute("value") : "Not available";
					String min = el.getAttribute("min") != null ? el.getAttribute("min") : "Not available";
					String max = el.getAttribute("max") != null ? el.getAttribute("max") : "Not available";
					otherElements.add(new OtherElement("meter", "Meter", value, min, max, "", "", "", "", ""));
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Error extracting progress/meter elements: " + e.getMessage());
		}

		LOGGER.info("Extracted " + otherElements.size() + " other elements");
		return otherElements;
	}

	/**
	 * Get a distinct color for element highlighting based on index
	 */
	private String getHighlightColor(int index) {
		String[] colors = { "#FF6B6B", // Red
				"#4ECDC4", // Teal
				"#45B7D1", // Blue
				"#FFA07A", // Light Salmon
				"#98D8C8", // Mint
				"#F7DC6F", // Yellow
				"#BB8FCE", // Purple
				"#85C1E2", // Sky Blue
				"#F8B739", // Orange
				"#52BE80", // Green
				"#E74C3C", // Dark Red
				"#3498DB", // Bright Blue
				"#9B59B6", // Violet
				"#E67E22", // Dark Orange
				"#1ABC9C", // Turquoise
				"#F39C12", // Gold
				"#E91E63", // Pink
				"#00BCD4", // Cyan
				"#8BC34A", // Light Green
				"#FF9800" // Deep Orange
		};
		return colors[index % colors.length];
	}

	/**
	 * Scroll through the entire page from top to bottom to trigger animations
	 */
	private void scrollPageTopToBottom(WebDriver driver) {
		try {
			JavascriptExecutor js = (JavascriptExecutor) driver;
			LOGGER.info("Scrolling page from top to bottom to trigger animations...");

			// Get page dimensions
			Long pageHeight = (Long) js.executeScript("return document.body.scrollHeight");
			Long viewportHeight = (Long) js.executeScript("return window.innerHeight");

			// Scroll in increments to ensure all animations are triggered
			long currentScroll = 0;
			long scrollIncrement = viewportHeight / 2; // Scroll by half viewport height

			while (currentScroll < pageHeight) {
				js.executeScript("window.scrollTo(0, " + currentScroll + ");");
				Thread.sleep(200); // Small delay to allow animations to trigger
				currentScroll += scrollIncrement;
			}

			// Ensure we reach the bottom
			js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
			Thread.sleep(500); // Wait a bit longer at the bottom

			LOGGER.info("Finished scrolling to bottom of page");
		} catch (Exception e) {
			LOGGER.warning("Error scrolling page top to bottom: " + e.getMessage());
		}
	}

	/**
	 * Scroll back to the top of the page
	 */
	private void scrollToTop(WebDriver driver) {
		try {
			JavascriptExecutor js = (JavascriptExecutor) driver;
			LOGGER.info("Scrolling back to top of page...");
			js.executeScript("window.scrollTo(0, 0);");
			Thread.sleep(500); // Wait for scroll to complete
			LOGGER.info("Reached top of page");
		} catch (Exception e) {
			LOGGER.warning("Error scrolling to top: " + e.getMessage());
		}
	}

	/**
	 * Scroll element into view smoothly
	 */
	private void scrollElementIntoView(WebDriver driver, WebElement element) {
		try {
			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", element);
			Thread.sleep(300); // Wait for scroll to complete and element to be visible
		} catch (Exception e) {
			LOGGER.warning("Error scrolling element into view: " + e.getMessage());
		}
	}

	/**
	 * Generate a unique identifier for an element based on its properties (tag,
	 * text, attributes) This is more reliable than location-based identification
	 * since location can change during scrolling
	 */
	private String generateElementIdentifier(WebElement element, String elementType) {
		try {
			StringBuilder identifier = new StringBuilder();

			// Add element type
			identifier.append(elementType).append("_");

			// Add tag name
			String tagName = element.getTagName().toLowerCase();
			identifier.append(tagName).append("_");

			// Add text content (normalized)
			String text = "";
			try {
				text = element.getText();
				if (text == null || text.trim().isEmpty()) {
					// Try to get text from textContent attribute
					text = element.getAttribute("textContent");
				}
			} catch (Exception e) {
				// Ignore if we can't get text
			}

			if (text != null && !text.trim().isEmpty()) {
				// Normalize text: remove extra whitespace, take first 100 chars
				String normalizedText = text.trim().replaceAll("\\s+", " ").substring(0,
						Math.min(100, text.trim().length()));
				identifier.append(normalizedText.hashCode()).append("_");
			} else {
				identifier.append("notext_");
			}

			// Add key attributes based on element type
			if (tagName.equals("a") || tagName.equals("link")) {
				String href = element.getAttribute("href");
				if (href != null && !href.isEmpty()) {
					identifier.append("href:").append(href).append("_");
				}
			} else if (tagName.equals("img")) {
				String src = element.getAttribute("src");
				String alt = element.getAttribute("alt");
				if (src != null && !src.isEmpty()) {
					identifier.append("src:").append(src).append("_");
				}
				if (alt != null && !alt.isEmpty()) {
					identifier.append("alt:").append(alt).append("_");
				}
			} else if (tagName.equals("button") || tagName.equals("input")) {
				String type = element.getAttribute("type");
				String value = element.getAttribute("value");
				if (type != null && !type.isEmpty()) {
					identifier.append("type:").append(type).append("_");
				}
				if (value != null && !value.isEmpty()) {
					identifier.append("value:").append(value).append("_");
				}
			}

			// Add id attribute if present (very reliable for uniqueness)
			String id = element.getAttribute("id");
			if (id != null && !id.isEmpty()) {
				identifier.append("id:").append(id).append("_");
			}

			// Add class attribute if present (helps with uniqueness)
			String className = element.getAttribute("class");
			if (className != null && !className.isEmpty()) {
				// Normalize class names (sort them for consistency)
				String[] classes = className.trim().split("\\s+");
				Arrays.sort(classes);
				identifier.append("class:").append(String.join(" ", classes)).append("_");
			}

			return identifier.toString();
		} catch (Exception e) {
			// Fallback to a basic identifier if we can't get properties
			try {
				return elementType + "_" + element.getTagName().toLowerCase() + "_" + element.getLocation().getX() + "_"
						+ element.getLocation().getY();
			} catch (Exception ex) {
				return elementType + "_" + System.identityHashCode(element);
			}
		}
	}

	/**
	 * Helper class to hold element information with position for sorting
	 */
	private static class ElementWithPosition {
		WebElement element;
		String elementType;
		int yPosition;
		int index;

		ElementWithPosition(WebElement element, String elementType, int yPosition, int index) {
			this.element = element;
			this.elementType = elementType;
			this.yPosition = yPosition;
			this.index = index;
		}
	}

	/**
	 * Capture screenshot with a highlighted element using JavaScript
	 */
	private String captureScreenshotWithHighlight(WebDriver driver, WebElement element, String highlightColor) {
		try {
			// Use JavaScript to add a temporary highlight border to the element
			JavascriptExecutor js = (JavascriptExecutor) driver;

			// Store original styles and apply strong, visible highlight
			@SuppressWarnings("unchecked")
			Map<String, Object> originalStyles = (Map<String, Object>) js.executeScript("var el = arguments[0]; "
					+ "var color = arguments[1]; " + "var original = { " + "  outline: el.style.outline, "
					+ "  outlineOffset: el.style.outlineOffset, " + "  boxShadow: el.style.boxShadow, "
					+ "  zIndex: el.style.zIndex, " + "  position: el.style.position " + "}; "
					+ "el.style.outline = color + ' solid 6px'; " + "el.style.outlineOffset = '3px'; "
					+ "el.style.boxShadow = '0 0 0 3px ' + color + ', 0 0 20px rgba(0,0,0,0.5)'; "
					+ "el.style.zIndex = '99999'; " + "if (window.getComputedStyle(el).position === 'static') { "
					+ "  el.style.position = 'relative'; " + "} " + "return original;", element, highlightColor);

			// Longer delay to ensure the highlight is fully rendered and visible
			Thread.sleep(200);

			// Capture screenshot
			TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
			byte[] screenshotBytes = screenshotDriver.getScreenshotAs(OutputType.BYTES);
			String screenshotBase64 = Base64.getEncoder().encodeToString(screenshotBytes);

			// Remove the highlight and restore original styles
			js.executeScript("var el = arguments[0]; " + "var original = arguments[1]; "
					+ "el.style.outline = original.outline || ''; "
					+ "el.style.outlineOffset = original.outlineOffset || ''; "
					+ "el.style.boxShadow = original.boxShadow || ''; " + "el.style.zIndex = original.zIndex || ''; "
					+ "if (original.position) { " + "  el.style.position = original.position; " + "} else { "
					+ "  el.style.position = ''; " + "}", element, originalStyles);

			return screenshotBase64;
		} catch (Exception e) {
			LOGGER.warning("Failed to capture screenshot with highlight: " + e.getMessage());
			// Fallback to regular screenshot
			try {
				TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
				byte[] screenshotBytes = screenshotDriver.getScreenshotAs(OutputType.BYTES);
				return Base64.getEncoder().encodeToString(screenshotBytes);
			} catch (Exception ex) {
				return "";
			}
		}
	}

	/**
	 * Get element position and size
	 */
	private Map<String, Integer> getElementBounds(WebElement element) {
		Map<String, Integer> bounds = new HashMap<>();
		try {
			Point location = element.getLocation();
			Dimension size = element.getSize();
			bounds.put("x", location.getX());
			bounds.put("y", location.getY());
			bounds.put("width", size.getWidth());
			bounds.put("height", size.getHeight());
		} catch (Exception e) {
			LOGGER.warning("Failed to get element bounds: " + e.getMessage());
			bounds.put("x", 0);
			bounds.put("y", 0);
			bounds.put("width", 0);
			bounds.put("height", 0);
		}
		return bounds;
	}

	/**
	 * Extract all element properties as a map
	 */
	private Map<String, String> extractElementProperties(WebElement element, String elementType) {
		Map<String, String> properties = new HashMap<>();
		try {
			// Extract properties BEFORE any highlighting is applied
			// This ensures we get the actual element styles, not highlight artifacts

			if (elementType.equals("heading") || elementType.equals("paragraph")) {
				String fontFamily = element.getCssValue("font-family");
				String fontSize = element.getCssValue("font-size");
				String fontWeight = element.getCssValue("font-weight");
				String lineHeight = element.getCssValue("line-height");
				String letterSpacing = element.getCssValue("letter-spacing");
				String color = element.getCssValue("color");

				if (fontFamily != null && !fontFamily.isEmpty() && !fontFamily.equals("initial")) {
					properties.put("font-family", fontFamily);
				}
				if (fontSize != null && !fontSize.isEmpty() && !fontSize.equals("initial")) {
					properties.put("font-size", fontSize);
				}
				if (fontWeight != null && !fontWeight.isEmpty() && !fontWeight.equals("initial")) {
					properties.put("font-weight", fontWeight);
				}
				if (lineHeight != null && !lineHeight.isEmpty() && !lineHeight.equals("initial")) {
					properties.put("line-height", lineHeight);
				}
				if (letterSpacing != null && !letterSpacing.isEmpty() && !letterSpacing.equals("initial")) {
					properties.put("letter-spacing", letterSpacing);
				}
				if (color != null && !color.isEmpty() && !color.equals("initial")
						&& !color.equals("rgba(0, 0, 0, 0)")) {
					properties.put("color", color);
				}
			} else if (elementType.equals("button") || elementType.equals("link")) {
				// Get background-color but filter out transparent/initial values
				String bgColor = element.getCssValue("background-color");
				if (bgColor != null && !bgColor.isEmpty() && !bgColor.equals("initial")
						&& !bgColor.equals("rgba(0, 0, 0, 0)") && !bgColor.equals("transparent")) {
					// Only add if it's a real background color (not transparent)
					properties.put("background-color", bgColor);
				}

				String color = element.getCssValue("color");
				if (color != null && !color.isEmpty() && !color.equals("initial")
						&& !color.equals("rgba(0, 0, 0, 0)")) {
					properties.put("color", color);
				}

				String fontSize = element.getCssValue("font-size");
				if (fontSize != null && !fontSize.isEmpty() && !fontSize.equals("initial")) {
					properties.put("font-size", fontSize);
				}

				String fontWeight = element.getCssValue("font-weight");
				if (fontWeight != null && !fontWeight.isEmpty() && !fontWeight.equals("initial")) {
					properties.put("font-weight", fontWeight);
				}

				String padding = element.getCssValue("padding");
				if (padding != null && !padding.isEmpty() && !padding.equals("initial") && !padding.equals("0px")) {
					properties.put("padding", padding);
				}

				String borderRadius = element.getCssValue("border-radius");
				if (borderRadius != null && !borderRadius.isEmpty() && !borderRadius.equals("initial")
						&& !borderRadius.equals("0px")) {
					properties.put("border-radius", borderRadius);
				}

				String lineHeight = element.getCssValue("line-height");
				if (lineHeight != null && !lineHeight.isEmpty() && !lineHeight.equals("initial")) {
					properties.put("line-height", lineHeight);
				}

				String letterSpacing = element.getCssValue("letter-spacing");
				if (letterSpacing != null && !letterSpacing.isEmpty() && !letterSpacing.equals("initial")) {
					properties.put("letter-spacing", letterSpacing);
				}

				String fontFamily = element.getCssValue("font-family");
				if (fontFamily != null && !fontFamily.isEmpty() && !fontFamily.equals("initial")) {
					properties.put("font-family", fontFamily);
				}

				if (elementType.equals("link")) {
					String href = element.getAttribute("href");
					if (href != null && !href.isEmpty()) {
						properties.put("href", href);
					}
				}
			} else {
				// For other elements, extract common properties
				properties.put("tag-name", element.getTagName());
				String id = element.getAttribute("id");
				String className = element.getAttribute("class");
				if (id != null && !id.isEmpty())
					properties.put("id", id);
				if (className != null && !className.isEmpty())
					properties.put("class", className);

				// For images, get src and alt
				if (element.getTagName().equalsIgnoreCase("img")) {
					String src = element.getAttribute("src");
					String alt = element.getAttribute("alt");
					if (src != null && !src.isEmpty())
						properties.put("src", src);
					if (alt != null && !alt.isEmpty())
						properties.put("alt", alt);
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Failed to extract element properties: " + e.getMessage());
		}
		return properties;
	}

	/**
	 * Capture screenshots for all elements in a section
	 */
	private void captureElementScreenshots(WebDriver driver, List<ElementScreenshotData> screenshotList,
			List<WebElement> elements, String elementType, int startIndex) {
		for (int i = 0; i < elements.size(); i++) {
			try {
				WebElement element = elements.get(i);
				if (!element.isDisplayed()) {
					continue;
				}

				String highlightColor = getHighlightColor(startIndex + i);
				String screenshotBase64 = captureScreenshotWithHighlight(driver, element, highlightColor);
				Map<String, Integer> bounds = getElementBounds(element);
				Map<String, String> properties = extractElementProperties(element, elementType);
				String text = element.getText();
				if (text == null || text.trim().isEmpty()) {
					text = element.getAttribute("textContent");
				}
				if (text == null)
					text = "Not available";

				String elementId = elementType + "-" + (startIndex + i);
				ElementScreenshotData screenshotData = new ElementScreenshotData(elementType, elementId,
						screenshotBase64, highlightColor, bounds.get("x"), bounds.get("y"), bounds.get("width"),
						bounds.get("height"), properties, text.length() > 100 ? text.substring(0, 100) + "..." : text,
						startIndex + i);
				screenshotList.add(screenshotData);
			} catch (Exception e) {
				LOGGER.warning(
						"Failed to capture screenshot for " + elementType + " element " + i + ": " + e.getMessage());
			}
		}
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
			if (actualText.length() < 3) {
				LOGGER.fine("Skipping very short text: " + actualText);
				return issues;
			}

			// Initialize LanguageTool
			JLanguageTool langTool;
			try {
				langTool = new JLanguageTool(new AmericanEnglish());
				LOGGER.fine("LanguageTool initialized successfully for text: "
						+ actualText.substring(0, Math.min(50, actualText.length())));
			} catch (Exception e) {
				LOGGER.warning("Failed to initialize LanguageTool: " + e.getMessage());
				return issues;
			}

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

			LOGGER.fine("LanguageTool found " + matches.size() + " potential issues for text: "
					+ actualText.substring(0, Math.min(50, actualText.length())));

			// If no matches found, log it for debugging
			if (matches.isEmpty() && actualText.length() > 10) {
				LOGGER.fine(
						"No LanguageTool matches for: " + actualText.substring(0, Math.min(100, actualText.length())));
			}

			for (RuleMatch match : matches) {
				String ruleId = match.getRule().getId().toUpperCase();
				String ruleCategory = match.getRule().getCategory().getName().toUpperCase();
				String ruleMessage = match.getMessage().toLowerCase();

				// Check if this is a spelling-related rule
				// LanguageTool spelling rules can have various IDs:
				// - MORFOLOGIK_RULE_* (most common)
				// - HUNSPELL_RULE
				// - SPELLING_RULE
				// - Rules in "Possible Typo" category
				boolean isSpellingError = ruleId.contains("SPELL") || ruleId.contains("MORFOLOGIK")
						|| ruleId.contains("HUNSPELL") || ruleId.contains("TYPO") || ruleId.contains("MISSPELLING")
						|| ruleCategory.contains("POSSIBLE TYPO") || ruleCategory.contains("SPELLING")
						|| ruleCategory.contains("TYPO") || ruleMessage.contains("spell")
						|| ruleMessage.contains("typo") || ruleMessage.contains("misspelling")
						|| ruleMessage.contains("did you mean");

				// Also check if the match has suggestions (spelling errors usually have
				// suggestions)
				boolean hasSuggestions = !match.getSuggestedReplacements().isEmpty();

				if (isSpellingError || (hasSuggestions && match.getSuggestedReplacements().size() > 0)) {
					String context = actualText.substring(match.getFromPos(), match.getToPos());
					String suggestion = match.getSuggestedReplacements().isEmpty() ? "No suggestions available"
							: String.join(", ", match.getSuggestedReplacements());

					// Create a more detailed message
					String detailedMessage = match.getMessage() + " (Rule: " + match.getRule().getId() + ")";

					issues.add(new GrammarIssue(context, suggestion, detailedMessage, elementType, actualText));
					LOGGER.warning("Spelling error found in " + elementType + ": '" + context + "' -> " + suggestion
							+ " (Rule: " + match.getRule().getId() + ", Category: "
							+ match.getRule().getCategory().getName() + ")");
				} else {
					LOGGER.fine("Skipping non-spelling rule: " + match.getRule().getId() + " ("
							+ match.getRule().getCategory().getName() + ") - Message: " + match.getMessage());
				}
			}

			if (matches.size() > 0 && issues.isEmpty()) {
				LOGGER.info("Found " + matches.size()
						+ " LanguageTool matches but none were identified as spelling errors for: "
						+ actualText.substring(0, Math.min(50, actualText.length())));
				// Log first few rule IDs for debugging
				for (int i = 0; i < Math.min(3, matches.size()); i++) {
					RuleMatch m = matches.get(i);
					LOGGER.info("  Rule " + (i + 1) + ": " + m.getRule().getId() + " - "
							+ m.getRule().getCategory().getName() + " - " + m.getMessage());
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
			HttpURLConnection connection = null;
			try {
				URL url = new URL(urlStr);
				connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(15000); // Increased timeout to 15 seconds
				connection.setReadTimeout(15000); // Increased timeout to 15 seconds
				connection.setInstanceFollowRedirects(false); // Manual redirect handling
				connection.setRequestProperty("User-Agent",
						"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
				connection.setRequestProperty("Accept",
						"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
				connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
				connection.setRequestProperty("Accept-Encoding", "gzip, deflate");

				// Connect and get response code
				// Note: getResponseCode() may throw IOException for error responses, we handle
				// that
				int status = -1;
				try {
					status = connection.getResponseCode();
				} catch (IOException e) {
					// For error responses (4xx, 5xx), getResponseCode() throws IOException
					// We need to use getErrorStream() to read the response
					status = connection.getResponseCode(); // This will work after the exception
					LOGGER.info("Got IOException (expected for error responses), status: " + status);
				}

				// Handle redirects manually to ensure we get the final status
				int redirectCount = 0;
				String currentUrl = urlStr;
				while (status >= 300 && status < 400 && redirectCount < 10) {
					String location = connection.getHeaderField("Location");
					if (location != null && !location.isEmpty()) {
						LOGGER.info("Following redirect #" + (redirectCount + 1) + " from " + currentUrl + " to "
								+ location);
						if (connection != null) {
							connection.disconnect();
						}

						// Resolve relative redirect URLs
						URL redirectUrl = new URL(new URL(currentUrl), location);
						currentUrl = redirectUrl.toString();

						connection = (HttpURLConnection) redirectUrl.openConnection();
						connection.setRequestMethod("GET");
						connection.setConnectTimeout(15000);
						connection.setReadTimeout(15000);
						connection.setInstanceFollowRedirects(false);
						connection.setRequestProperty("User-Agent",
								"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
						connection.setRequestProperty("Accept",
								"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
						connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
						connection.setRequestProperty("Accept-Encoding", "gzip, deflate");

						try {
							status = connection.getResponseCode();
						} catch (IOException e) {
							status = connection.getResponseCode();
						}
						redirectCount++;
					} else {
						break;
					}
				}

				// Logging the status for debugging - always log 404s and errors
				if (status == 404) {
					LOGGER.warning("*** 404 NOT FOUND *** - URL: " + currentUrl + " (original: " + urlStr + ")");
				} else if (status >= 400) {
					LOGGER.info("Error status (non-404) - URL: " + currentUrl + ", Status: " + status);
				} else {
					LOGGER.info("URL: " + currentUrl + ", Status: " + status);
				}

				// Check for soft 404s (pages that return 200 but show 404 content)
				if (status == 200) {
					try {
						InputStream inputStream = connection.getInputStream();
						BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
						String content = reader.lines().collect(Collectors.joining());
						reader.close();

						// Detect soft 404 pages - look for common 404 error page patterns
						String lowerContent = content.toLowerCase();
						if (lowerContent.contains("404 not found") || lowerContent.contains("page not found")
								|| lowerContent.contains("the page you requested was not found")
								|| lowerContent.contains("error 404") || lowerContent.contains("http 404")
								|| (lowerContent.contains("404") && lowerContent.contains("not found"))
								|| lowerContent.contains("page cannot be found")
								|| lowerContent.contains("file not found")
								|| lowerContent.contains("resource not found")
								|| lowerContent.contains("nothing was found")
								|| lowerContent.contains("page does not exist")) {
							LOGGER.warning("Soft 404 detected (200 with 404 content) for URL: " + currentUrl);
							if (connection != null)
								connection.disconnect();
							return 404; // Treat soft 404s as broken links
						}
					} catch (Exception e) {
						LOGGER.warning("Error reading content for soft 404 check: " + e.getMessage());
					}
				}

				// Clean up connection
				if (connection != null) {
					connection.disconnect();
				}

				// Return the actual status code (only 404 will be caught by the calling code)
				return status;

			} catch (MalformedURLException e) {
				LOGGER.warning("Malformed URL: " + urlStr + " - " + e.getMessage());
				return -1;
			} catch (IOException e) {
				retries--;
				LOGGER.warning("IO Exception getting HTTP status for URL: " + urlStr + ", Retries left: " + retries
						+ ", Error: " + e.getMessage());
				if (retries == 0) {
					return -1; // Return -1 for failed attempts
				}
				try {
					Thread.sleep(2000); // Wait 2 seconds before retry
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return -1;
				}
			} catch (Exception e) {
				retries--;
				LOGGER.warning("Unexpected error getting HTTP status for URL: " + urlStr + ", Retries left: " + retries
						+ ", Error: " + e.getMessage());
				e.printStackTrace();
				if (retries == 0) {
					return -1;
				}
				try {
					Thread.sleep(2000);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return -1;
				}
			} finally {
				if (connection != null) {
					try {
						connection.disconnect();
					} catch (Exception e) {
						// Ignore disconnect errors
					}
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
		String linkHealth = data.brokenLinks.isEmpty() ? "No broken links"
				: data.brokenLinks.size() + " 404 broken links";
		details.append(escapeHtml(linkHealth));
		return details.toString();
	}

	private void generateResolutionContent(StringBuilder sb, WebsiteData data, String resolutionName) {
		// Add unique IDs for this resolution to avoid conflicts
		String resolutionId = resolutionName.replaceAll("[^a-zA-Z0-9]", "-").toLowerCase();

		int headingCount = data.headers.values().stream().mapToInt(List::size).sum();
		List<ButtonOrLink> buttonElements = data.buttons.stream().filter(b -> "button".equalsIgnoreCase(b.type))
				.collect(Collectors.toList());
		List<ButtonOrLink> linkElements = data.buttons.stream()
				.filter(b -> "a".equalsIgnoreCase(b.type) && b.href != null && !b.href.isEmpty())
				.collect(Collectors.toList());
		int elementsCount = buttonElements.size() + linkElements.size() + data.paragraphs.size();
		int issuesCount = data.grammarIssues.size() + data.brokenLinks.size();

		// Check if SEO Information should be excluded for this resolution
		boolean excludeSeo = RESOLUTIONS_WITHOUT_SEO.contains(resolutionName);

		sb.append("<div class=\"tabs\" role=\"tablist\" aria-label=\"Dashboard Sections Tabs\">");

		// Only add SEO Information tab if not excluded
		if (!excludeSeo) {
			sb.append("<div class=\"tab active\" role=\"tab\" tabindex=\"0\" aria-selected=\"true\" aria-controls=\"")
					.append(resolutionId).append("-seo-info\" id=\"").append(resolutionId)
					.append("-tab-seo-info\" data-tab=\"").append(resolutionId)
					.append("-seo-info\">SEO Information</div>\n");
		}

		// Headings tab - make it active if SEO is excluded
		sb.append("<div class=\"tab").append(excludeSeo ? " active" : "").append("\" role=\"tab\" tabindex=\"")
				.append(excludeSeo ? "0" : "-1").append("\" aria-selected=\"").append(excludeSeo ? "true" : "false")
				.append("\" aria-controls=\"").append(resolutionId).append("-headings\" id=\"").append(resolutionId)
				.append("-tab-headings\" data-tab=\"").append(resolutionId).append("-headings\">Headings (")
				.append(headingCount).append(")</div>\n");
		sb.append("<div class=\"tab\" role=\"tab\" tabindex=\"-1\" aria-selected=\"false\" aria-controls=\"")
				.append(resolutionId).append("-elements\" id=\"").append(resolutionId)
				.append("-tab-elements\" data-tab=\"").append(resolutionId).append("-elements\">Elements (")
				.append(elementsCount).append(")</div>\n");
		sb.append("<div class=\"tab\" role=\"tab\" tabindex=\"-1\" aria-selected=\"false\" aria-controls=\"")
				.append(resolutionId).append("-screenshots\" id=\"").append(resolutionId)
				.append("-tab-screenshots\" data-tab=\"").append(resolutionId).append("-screenshots\">Screenshots (")
				.append(data.elementScreenshots != null ? data.elementScreenshots.size() : 0).append(")</div>\n");
		sb.append("<div class=\"tab\" role=\"tab\" tabindex=\"-1\" aria-selected=\"false\" aria-controls=\"")
				.append(resolutionId).append("-issues\" id=\"").append(resolutionId).append("-tab-issues\" data-tab=\"")
				.append(resolutionId).append("-issues\">Issues (").append(issuesCount).append(")</div>\n");
		sb.append("</div>\n");

		// Only add SEO Information content if not excluded
		if (!excludeSeo) {
			sb.append("<div class=\"tab-content active\" id=\"").append(resolutionId)
					.append("-seo-info\" role=\"tabpanel\" tabindex=\"0\" aria-labelledby=\"").append(resolutionId)
					.append("-tab-seo-info\">\n");
			sb.append("<div class=\"seo-grid\" aria-label=\"URL and SEO Information\">\n");

			sb.append("<div class=\"seo-card\">\n");
			sb.append("<h3>URL</h3>\n");
			sb.append("<p><a href=\"").append(escapeHtml(data.url))
					.append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"link-url\">")
					.append(escapeHtml(data.url)).append("</a></p>\n");
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
				sb.append(
						"<span class=\"warning\" aria-label=\"Meta description too long\">Description exceeds recommended length (160 characters)</span>");
			}
			sb.append("</p>\n");
			sb.append("</div>\n");

			sb.append("</div>\n");

			int seoScore = calculateSeoScore(data);
			String[] seoDetails = generateSeoScoreDetails(data).split("\\|");
			sb.append("<div class=\"seo-score-container\" aria-label=\"SEO Score\">\n");
			sb.append("<div class=\"score-circle\">").append(seoScore).append(" <span>/ 100</span></div>\n");
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
		}

		// Headings tab content - make it active if SEO is excluded
		sb.append("<div class=\"tab-content").append(excludeSeo ? " active" : "").append("\" id=\"")
				.append(resolutionId).append("-headings\" role=\"tabpanel\" tabindex=\"")
				.append(excludeSeo ? "0" : "-1").append("\" aria-labelledby=\"").append(resolutionId)
				.append("-tab-headings\">\n");
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
					.append(i == 1 ? "true" : "false").append("\" aria-controls=\"").append(resolutionId)
					.append("-heading-").append(tag).append("\" id=\"").append(resolutionId).append("-subtab-heading-")
					.append(tag).append("\" data-tab=\"").append(resolutionId).append("-heading-").append(tag)
					.append("\">").append(tag.toUpperCase()).append("</div>\n");
		}
		sb.append("</div>\n");

		for (int i = 1; i <= 6; i++) {
			String tag = "h" + i;
			List<HeadingElement> headings = data.headers.getOrDefault(tag, Collections.emptyList());
			sb.append("<div class=\"heading-detail").append(i == 1 ? " active" : "").append("\" id=\"")
					.append(resolutionId).append("-heading-").append(tag).append("\" role=\"tabpanel\" tabindex=\"")
					.append(i == 1 ? "0" : "-1").append("\" aria-labelledby=\"").append(resolutionId)
					.append("-subtab-heading-").append(tag).append("\">\n");
			if (headings.isEmpty()) {
				sb.append("<p><strong>No ").append(tag.toUpperCase()).append(" headings found.</strong></p>\n");
			} else {
				for (HeadingElement h : headings) {
					sb.append("<div class=\"paragraph-card\">\n");
					sb.append("<div class=\"heading-text-display\">").append(tag.toUpperCase()).append(": ")
							.append(escapeHtml(h.text)).append("</div>\n");
					sb.append("<div class=\"details\">\n");
					sb.append("<div><strong>Font Family:</strong> <span>").append(escapeHtml(h.fontFamily))
							.append("</span></div>\n");
					sb.append("<div><strong>Font Size:</strong> <span>").append(escapeHtml(h.fontSize))
							.append("</span></div>\n");
					sb.append("<div><strong>Font Weight:</strong> <span>").append(escapeHtml(h.fontWeight)).append(" ")
							.append(escapeHtml(FONT_WEIGHT_NAMES.getOrDefault(h.fontWeight, ""))).append("</span></div>\n");
					sb.append("<div><strong>Line Height:</strong> <span>").append(escapeHtml(h.lineHeight))
							.append("</span></div>\n");
					sb.append("<div><strong>Letter Spacing:</strong> <span>").append(escapeHtml(h.letterSpacing))
							.append("</span></div>\n");
					sb.append("<div><strong>Color:</strong> <span><span class=\"color-box\" style=\"background: ")
							.append(escapeHtml(cssColorToHex(h.color))).append("\"></span> ")
							.append(escapeHtml(cssColorToHex(h.color))).append("</span></div>\n");
					sb.append("</div>\n");
					sb.append("</div>\n");
				}
			}
			sb.append("</div>\n");
		}
		sb.append("</div>\n");

		sb.append("<div class=\"tab-content\" id=\"").append(resolutionId)
				.append("-elements\" role=\"tabpanel\" tabindex=\"-1\" aria-labelledby=\"").append(resolutionId)
				.append("-tab-elements\">\n");
		sb.append("<div class=\"elements-overview\" aria-label=\"Elements Overview\">\n");
		sb.append("<div class=\"element-box\">").append(buttonElements.size()).append(" BUTTONS</div>\n");
		sb.append("<div class=\"element-box\">").append(linkElements.size()).append(" LINKS</div>\n");
		sb.append("<div class=\"element-box\">").append(data.paragraphs.size()).append(" PARAGRAPHS</div>\n");
		sb.append("</div>\n");

		sb.append("<div class=\"elements-subtabs\" role=\"tablist\" aria-label=\"Elements sub tabs\">\n");
		sb.append(
				"<button class=\"elements-tab active\" role=\"tab\" aria-selected=\"true\" tabindex=\"0\" data-tab=\"")
				.append(resolutionId).append("-buttons\">Buttons</button>\n");
		sb.append("<button class=\"elements-tab\" role=\"tab\" aria-selected=\"false\" tabindex=\"-1\" data-tab=\"")
				.append(resolutionId).append("-links\">Links</button>\n");
		sb.append("<button class=\"elements-tab\" role=\"tab\" aria-selected=\"false\" tabindex=\"-1\" data-tab=\"")
				.append(resolutionId).append("-other-elements\">All Other Sections</button>\n");
		sb.append("<button class=\"elements-tab\" role=\"tab\" aria-selected=\"false\" tabindex=\"-1\" data-tab=\"")
				.append(resolutionId).append("-paragraphs\">Paragraphs</button>\n");
		sb.append("</div>\n");

		sb.append("<div class=\"element-details\" id=\"").append(resolutionId)
				.append("-buttons\" role=\"tabpanel\" tabindex=\"0\">\n");
		sb.append("<div class=\"element-header\">Button Elements</div>\n");
		if (buttonElements.isEmpty()) {
			sb.append("<p>No buttons found.</p>\n");
		} else {
			for (ButtonOrLink b : buttonElements) {
				sb.append("<div class=\"paragraph-card\">\n");
				sb.append("<div class=\"heading-text-display\">").append(escapeHtml(b.text)).append("</div>\n");
				sb.append("<div class=\"details\">\n");
				if (!b.ariaLabel.equals("Not available")) {
					sb.append("<div><strong>ARIA Label:</strong> <span>")
							.append(escapeHtml(b.ariaLabel)).append("</span></div>\n");
				}
				if (!b.fontFamily.equals("Not available")) {
					sb.append("<div><strong>Font Family:</strong> <span>")
							.append(escapeHtml(b.fontFamily)).append("</span></div>\n");
				}
				if (!b.fontSize.equals("Not available")) {
					sb.append("<div><strong>Font Size:</strong> <span>").append(escapeHtml(b.fontSize))
							.append("</span></div>\n");
				}
				if (!b.fontWeight.equals("Not available")) {
					sb.append("<div><strong>Font Weight:</strong> <span>")
							.append(escapeHtml(b.fontWeight)).append(" ")
							.append(escapeHtml(FONT_WEIGHT_NAMES.getOrDefault(b.fontWeight, "")))
							.append("</span></div>\n");
				}
				if (!b.lineHeight.equals("Not available")) {
					sb.append("<div><strong>Line Height:</strong> <span>")
							.append(escapeHtml(b.lineHeight)).append("</span></div>\n");
				}
				if (!b.letterSpacing.equals("Not available")) {
					sb.append("<div><strong>Letter Spacing:</strong> <span>")
							.append(escapeHtml(b.letterSpacing)).append("</span></div>\n");
				}
				if (!b.padding.equals("Not available")) {
					sb.append("<div><strong>Padding:</strong> <span>").append(escapeHtml(b.padding))
							.append("</span></div>\n");
				}
				if (!b.borderRadius.equals("Not available")) {
					sb.append("<div><strong>Border Radius:</strong> <span>")
							.append(escapeHtml(b.borderRadius)).append("</span></div>\n");
				}
				if (!b.buttonColor.equals("Not available")) {
					sb.append(
							"<div><strong>Background Color:</strong> <span><span class=\"color-box button-color-box\" style=\"background: ")
							.append(escapeHtml(cssColorToHex(b.buttonColor))).append(";\"></span> ")
							.append(escapeHtml(cssColorToHex(b.buttonColor))).append("</span></div>\n");
				}
				if (!b.textColor.equals("Not available")) {
					sb.append(
							"<div><strong>Text Color:</strong> <span><span class=\"color-box button-color-box\" style=\"background: ")
							.append(escapeHtml(cssColorToHex(b.textColor))).append(";\"></span> ")
							.append(escapeHtml(cssColorToHex(b.textColor))).append("</span></div>\n");
				}
				if (b.href != null && !b.href.isEmpty() && !b.href.equals("Not available")) {
					sb.append("<div><strong>URL:</strong> <span><a href=\"").append(escapeHtml(b.href))
							.append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"link-url\">")
							.append(escapeHtml(b.href)).append("</a></span></div>\n");
				}
				sb.append("</div>\n");
				sb.append("</div>\n");
			}
		}
		sb.append("</div>\n");

		sb.append("<div class=\"element-details\" id=\"").append(resolutionId)
				.append("-links\" role=\"tabpanel\" tabindex=\"0\" hidden>\n");
		sb.append("<div class=\"element-header\">Link Elements</div>\n");
		if (linkElements.isEmpty()) {
			sb.append("<p>No links found.</p>\n");
		} else {
			for (ButtonOrLink l : linkElements) {
				sb.append("<div class=\"paragraph-card\">\n");
				sb.append("<div class=\"heading-text-display\">").append(escapeHtml(l.text)).append("</div>\n");
				sb.append("<div class=\"details\">\n");
				if (!l.fontFamily.equals("Not available")) {
					sb.append("<div><strong>Font Family:</strong> <span>")
							.append(escapeHtml(l.fontFamily)).append("</span></div>\n");
				}
				if (!l.fontSize.equals("Not available")) {
					sb.append("<div><strong>Font Size:</strong> <span>").append(escapeHtml(l.fontSize))
							.append("</span></div>\n");
				}
				if (!l.fontWeight.equals("Not available")) {
					sb.append("<div><strong>Font Weight:</strong> <span>")
							.append(escapeHtml(l.fontWeight)).append(" ")
							.append(escapeHtml(FONT_WEIGHT_NAMES.getOrDefault(l.fontWeight, "")))
							.append("</span></div>\n");
				}
				if (!l.lineHeight.equals("Not available")) {
					sb.append("<div><strong>Line Height:</strong> <span>")
							.append(escapeHtml(l.lineHeight)).append("</span></div>\n");
				}
				if (!l.letterSpacing.equals("Not available")) {
					sb.append("<div><strong>Letter Spacing:</strong> <span>")
							.append(escapeHtml(l.letterSpacing)).append("</span></div>\n");
				}
				if (!l.buttonColor.equals("Not available")) {
					sb.append(
							"<div><strong>Background Color:</strong> <span><span class=\"color-box button-color-box\" style=\"background: ")
							.append(escapeHtml(cssColorToHex(l.buttonColor))).append(";\"></span> ")
							.append(escapeHtml(cssColorToHex(l.buttonColor))).append("</span></div>\n");
				}
				if (!l.textColor.equals("Not available")) {
					sb.append(
							"<div><strong>Text Color:</strong> <span><span class=\"color-box button-color-box\" style=\"background: ")
							.append(escapeHtml(cssColorToHex(l.textColor))).append(";\"></span> ")
							.append(escapeHtml(cssColorToHex(l.textColor))).append("</span></div>\n");
				}
				sb.append("</div>\n");
				sb.append("<div class=\"url-box\"><strong>URL:</strong> <span><a href=\"").append(escapeHtml(l.href))
						.append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"link-url\">")
						.append(escapeHtml(l.href)).append("</a></span></div>\n");
				sb.append("</div>\n");
			}
		}
		sb.append("</div>\n");

		sb.append("<div class=\"element-details\" id=\"").append(resolutionId)
				.append("-other-elements\" role=\"tabpanel\" tabindex=\"0\" hidden>\n");
		sb.append("<div class=\"element-header\">All Other Sections</div>\n");
		if (data.otherElements == null || data.otherElements.isEmpty()) {
			sb.append("<p>No other elements found.</p>\n");
		} else {
			// Group elements by tag name
			Map<String, List<OtherElement>> groupedElements = new LinkedHashMap<>();
			for (OtherElement el : data.otherElements) {
				groupedElements.computeIfAbsent(el.tagName, k -> new ArrayList<>()).add(el);
			}

			for (Map.Entry<String, List<OtherElement>> entry : groupedElements.entrySet()) {
				String tagName = entry.getKey();
				List<OtherElement> elements = entry.getValue();
				sb.append("<details open>\n");
				sb.append("<summary>").append(tagName.toUpperCase()).append(" (").append(elements.size())
						.append(")</summary>\n");
				sb.append("<div>\n");

				for (OtherElement el : elements) {
					sb.append("<div class=\"paragraph-card\">\n");
					if (el.text != null && !el.text.isEmpty() && !el.text.equals("Not available")) {
						sb.append("<div class=\"heading-text-display\">")
								.append(escapeHtml(
										el.text.length() > 200 ? el.text.substring(0, 200) + "..." : el.text))
								.append("</div>\n");
					}
					sb.append("<div class=\"details\">\n");
					sb.append("<div><strong>Tag:</strong> <span>").append(escapeHtml(el.tagName))
							.append("</span></div>\n");

					// Display attributes based on tag type
					// Track if there's a URL to display below
					boolean hasUrl = false;
					String urlValue = "";
					if (el.tagName.equals("img")) {
						if (!el.attr1.isEmpty() && !el.attr1.equals("Not available")) {
							hasUrl = true;
							urlValue = el.attr1;
						}
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Alt Text:</strong> <span>")
									.append(escapeHtml(el.attr2)).append("</span></div>\n");
						}
						if (!el.attr3.isEmpty() && !el.attr3.equals("Not available")) {
							sb.append("<div><strong>Title:</strong> <span>")
									.append(escapeHtml(el.attr3)).append("</span></div>\n");
						}
						if (!el.attr4.isEmpty() && !el.attr4.equals("Not available")) {
							sb.append("<div><strong>Width:</strong> <span>")
									.append(escapeHtml(el.attr4)).append("</span></div>\n");
						}
						if (!el.attr5.isEmpty() && !el.attr5.equals("Not available")) {
							sb.append("<div><strong>Height:</strong> <span>")
									.append(escapeHtml(el.attr5)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("ul") || el.tagName.equals("ol")) {
						if (!el.attr7.isEmpty() && !el.attr7.equals("Not available")) {
							sb.append("<div><strong>List Items:</strong> <span>")
									.append(escapeHtml(el.attr7)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("table")) {
						if (!el.attr7.isEmpty() && !el.attr7.equals("Not available")) {
							sb.append("<div><strong>Rows:</strong> <span>").append(escapeHtml(el.attr7))
									.append("</span></div>\n");
						}
						if (!el.attr8.isEmpty() && !el.attr8.equals("Not available")) {
							sb.append("<div><strong>Columns:</strong> <span>")
									.append(escapeHtml(el.attr8)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("form")) {
						if (!el.attr1.isEmpty() && !el.attr1.equals("Not available")) {
							sb.append("<div><strong>Action:</strong> <span>")
									.append(escapeHtml(el.attr1)).append("</span></div>\n");
						}
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Method:</strong> <span>")
									.append(escapeHtml(el.attr2)).append("</span></div>\n");
						}
						if (!el.attr7.isEmpty() && !el.attr7.equals("Not available")) {
							sb.append("<div><strong>Form Fields:</strong> <span>")
									.append(escapeHtml(el.attr7)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("input")) {
						if (!el.attr1.isEmpty() && !el.attr1.equals("Not available")) {
							sb.append("<div><strong>Type:</strong> <span>").append(escapeHtml(el.attr1))
									.append("</span></div>\n");
						}
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Placeholder:</strong> <span>")
									.append(escapeHtml(el.attr2)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("textarea")) {
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Placeholder:</strong> <span>")
									.append(escapeHtml(el.attr2)).append("</span></div>\n");
						}
						if (!el.attr3.isEmpty() && !el.attr3.equals("Not available")) {
							sb.append("<div><strong>Rows:</strong> <span>").append(escapeHtml(el.attr3))
									.append("</span></div>\n");
						}
						if (!el.attr4.isEmpty() && !el.attr4.equals("Not available")) {
							sb.append("<div><strong>Columns:</strong> <span>")
									.append(escapeHtml(el.attr4)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("select")) {
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Options:</strong> <span>")
									.append(escapeHtml(el.attr2)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("label")) {
						if (!el.attr1.isEmpty() && !el.attr1.equals("Not available")) {
							sb.append("<div><strong>For:</strong> <span>").append(escapeHtml(el.attr1))
									.append("</span></div>\n");
						}
					} else if (el.tagName.equals("video") || el.tagName.equals("audio")
							|| el.tagName.equals("iframe")) {
						if (!el.attr1.isEmpty() && !el.attr1.equals("Not available")) {
							hasUrl = true;
							urlValue = el.attr1;
						}
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Width:</strong> <span>")
									.append(escapeHtml(el.attr2)).append("</span></div>\n");
						}
						if (!el.attr3.isEmpty() && !el.attr3.equals("Not available")) {
							sb.append("<div><strong>Height:</strong> <span>")
									.append(escapeHtml(el.attr3)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("svg")) {
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Width:</strong> <span>")
									.append(escapeHtml(el.attr2)).append("</span></div>\n");
						}
						if (!el.attr3.isEmpty() && !el.attr3.equals("Not available")) {
							sb.append("<div><strong>Height:</strong> <span>")
									.append(escapeHtml(el.attr3)).append("</span></div>\n");
						}
						if (!el.attr4.isEmpty() && !el.attr4.equals("Not available")) {
							sb.append("<div><strong>ViewBox:</strong> <span>")
									.append(escapeHtml(el.attr4)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("section") || el.tagName.equals("article")
							|| el.tagName.equals("aside") || el.tagName.equals("nav") || el.tagName.equals("header")
							|| el.tagName.equals("footer") || el.tagName.equals("main")) {
						if (!el.attr1.isEmpty() && !el.attr1.equals("Not available")) {
							sb.append("<div><strong>ID:</strong> <span>").append(escapeHtml(el.attr1))
									.append("</span></div>\n");
						}
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Class:</strong> <span>")
									.append(escapeHtml(el.attr2)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("details")) {
						if (!el.attr1.isEmpty() && !el.attr1.equals("Not available")) {
							sb.append("<div><strong>State:</strong> <span>")
									.append(escapeHtml(el.attr1)).append("</span></div>\n");
						}
					} else if (el.tagName.equals("progress") || el.tagName.equals("meter")) {
						if (!el.attr1.isEmpty() && !el.attr1.equals("Not available")) {
							sb.append("<div><strong>Value:</strong> <span>")
									.append(escapeHtml(el.attr1)).append("</span></div>\n");
						}
						if (!el.attr2.isEmpty() && !el.attr2.equals("Not available")) {
							sb.append("<div><strong>Max:</strong> <span>").append(escapeHtml(el.attr2))
									.append("</span></div>\n");
						}
					}

					sb.append("</div>\n");
					if (hasUrl && !urlValue.isEmpty()) {
						sb.append("<div class=\"url-box\"><strong>Source:</strong> <span><a href=\"")
								.append(escapeHtml(urlValue)).append("\" target=\"_blank\" class=\"link-url\">")
								.append(escapeHtml(urlValue)).append("</a></span></div>\n");
					}
					sb.append("</div>\n");
				}

				sb.append("</div>\n");
				sb.append("</details>\n");
			}
		}
		sb.append("</div>\n");

		sb.append("<div class=\"element-details\" id=\"").append(resolutionId)
				.append("-paragraphs\" role=\"tabpanel\" tabindex=\"0\" hidden>\n");
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

		// Screenshots tab content
		sb.append("<div class=\"tab-content\" id=\"").append(resolutionId)
				.append("-screenshots\" role=\"tabpanel\" tabindex=\"-1\" aria-labelledby=\"").append(resolutionId)
				.append("-tab-screenshots\">\n");
		sb.append("<div class=\"screenshot-section-title\">Element Screenshots with Details</div>\n");

		if (data.elementScreenshots == null || data.elementScreenshots.isEmpty()) {
			sb.append("<p>No element screenshots available.</p>\n");
		} else {
			// Display screenshots in visual order (top to bottom) as they appear on the
			// page
			// Screenshots are already sorted by Y position during capture
			sb.append("<div class=\"screenshot-visual-order-info\">");
			sb.append("<p style=\"margin-bottom: 20px; color: var(--text-secondary); font-size: 14px;\">");
			sb.append("Screenshots are displayed in visual order (top to bottom) as they appear on the webpage.");
			sb.append("</p>");
			sb.append("</div>\n");

			int totalScreenshots = data.elementScreenshots.size();
			// Create a wrapper container for all screenshots with fixed layout
			sb.append("<div class=\"screenshots-wrapper\">\n");
			for (int i = 0; i < data.elementScreenshots.size(); i++) {
				ElementScreenshotData screenshot = data.elementScreenshots.get(i);
				String screenshotId = resolutionId + "-screenshot-" + screenshot.elementId;

				// Wrapper for each screenshot with fixed height layout
				sb.append("<div class=\"screenshot-wrapper\" id=\"").append(screenshotId).append("\"");
				if (i > 0)
					sb.append(" style=\"display: none;\"");
				sb.append(">\n");

				// Content container (image + details)
				sb.append("<div class=\"screenshot-viewer-container\">\n");

				// Screenshot viewer
				sb.append("<div class=\"screenshot-viewer\">\n");
				sb.append("<img src=\"data:image/png;base64,").append(screenshot.screenshotBase64)
						.append("\" alt=\"Screenshot of ").append(escapeHtml(screenshot.elementType))
						.append(" element at position ").append(screenshot.y).append("\" />\n");
				sb.append("</div>\n");

				// Element details panel
				sb.append("<div class=\"element-details-panel\">\n");
				sb.append("<div class=\"element-details-header\">\n");
				sb.append("<div class=\"element-color-indicator\" style=\"background-color: ")
						.append(screenshot.highlightColor).append("; box-shadow: 0 0 8px ")
						.append(screenshot.highlightColor).append(";\"></div>\n");
				sb.append("<span>Element Details</span>\n");
				sb.append("</div>\n");

				// Element properties
				if (screenshot.properties != null && !screenshot.properties.isEmpty()) {
					sb.append("<div class=\"element-details-section\">\n");
					sb.append("<h4>Styling Properties</h4>\n");
					for (Map.Entry<String, String> prop : screenshot.properties.entrySet()) {
						if (prop.getValue() != null && !prop.getValue().isEmpty()
								&& !prop.getValue().equals("Not available")) {
							sb.append("<div class=\"property-row\">\n");
							sb.append("<span class=\"property-label\">")
									.append(escapeHtml(prop.getKey().replace("-", " "))).append(":</span>\n");
							String value = prop.getValue();
							if (prop.getKey().contains("color")) {
								sb.append(
										"<span class=\"property-value\"><span class=\"color-box\" style=\"background: ")
										.append(escapeHtml(cssColorToHex(value))).append(";\"></span> ")
										.append(escapeHtml(cssColorToHex(value))).append("</span>\n");
							} else {
								sb.append("<span class=\"property-value\">")
										.append(escapeHtml(
												value.length() > 50 ? value.substring(0, 50) + "..." : value))
										.append("</span>\n");
							}
							sb.append("</div>\n");
						}
					}
					sb.append("</div>\n");
				}

				// Element type and index
				sb.append("<div class=\"element-details-section\">\n");
				sb.append("<h4>Element Information</h4>\n");
				sb.append("<div class=\"property-row\">\n");
				sb.append("<span class=\"property-label\">Type:</span>\n");
				sb.append("<span class=\"property-value\">").append(escapeHtml(screenshot.elementType))
						.append("</span>\n");
				sb.append("</div>\n");
				sb.append("<div class=\"property-row\">\n");
				sb.append("<span class=\"property-label\">Visual Position:</span>\n");
				sb.append("<span class=\"property-value\">").append(i + 1).append(" of ").append(totalScreenshots)
						.append(" (Y: ").append(screenshot.y).append("px)</span>\n");
				sb.append("</div>\n");
				sb.append("<div class=\"property-row\">\n");
				sb.append("<span class=\"property-label\">Position:</span>\n");
				sb.append("<span class=\"property-value\">X: ").append(screenshot.x).append(", Y: ")
						.append(screenshot.y).append("</span>\n");
				sb.append("</div>\n");
				sb.append("<div class=\"property-row\">\n");
				sb.append("<span class=\"property-label\">Size:</span>\n");
				sb.append("<span class=\"property-value\">").append(screenshot.width).append(" × ")
						.append(screenshot.height).append(" px</span>\n");
				sb.append("</div>\n");
				sb.append("</div>\n");

				// Element text
				if (screenshot.text != null && !screenshot.text.isEmpty() && !screenshot.text.equals("Not available")) {
					sb.append("<div class=\"element-details-section\">\n");
					sb.append("<h4>Text Content</h4>\n");
					sb.append("<div class=\"element-text-preview\">").append(escapeHtml(screenshot.text))
							.append("</div>\n");
					sb.append("</div>\n");
				}

				sb.append("</div>\n"); // Close element-details-panel
				sb.append("</div>\n"); // Close screenshot-viewer-container

				// Navigation - fixed at bottom, outside the content container
				sb.append("<div class=\"screenshot-navigation\">\n");
				sb.append(
						"<button class=\"screenshot-nav-button\" onclick=\"navigateScreenshotFromButton(this, 'prev', event)\"");
				if (i == 0)
					sb.append(" disabled");
				sb.append(">Previous</button>\n");
				sb.append("<span class=\"screenshot-counter\">").append(i + 1).append(" / ").append(totalScreenshots)
						.append("</span>\n");
				sb.append(
						"<button class=\"screenshot-nav-button\" onclick=\"navigateScreenshotFromButton(this, 'next', event)\"");
				if (i == totalScreenshots - 1)
					sb.append(" disabled");
				sb.append(">Next</button>\n");
				sb.append("</div>\n");

				sb.append("</div>\n"); // Close screenshot-wrapper
			}
			sb.append("</div>\n"); // Close screenshots-wrapper
		}

		sb.append("</div>\n"); // Close tab-content for screenshots

		sb.append("<div class=\"tab-content\" id=\"").append(resolutionId)
				.append("-issues\" role=\"tabpanel\" tabindex=\"-1\" aria-labelledby=\"").append(resolutionId)
				.append("-tab-issues\">\n");
		sb.append("<div class=\"issues-overview\" aria-label=\"Issues Overview\">\n");
		sb.append("<div class=\"issue-box total\">").append(issuesCount).append(" TOTAL ISSUES</div>\n");
		sb.append("<div class=\"issue-box grammar\">").append(data.grammarIssues.size())
				.append(" SPELLING ISSUES</div>\n");
		sb.append("<div class=\"issue-box broken\">").append(data.brokenLinks.size()).append(" BROKEN LINKS</div>\n");
		sb.append("</div>\n");

		sb.append("<div class=\"issues-subtabs\" role=\"tablist\" aria-label=\"Issues sub tabs\">\n");
		sb.append("<button class=\"issues-tab active\" role=\"tab\" aria-selected=\"true\" tabindex=\"0\" data-tab=\"")
				.append(resolutionId).append("-grammar-issues\">Spelling Issues (").append(data.grammarIssues.size())
				.append(")</button>\n");
		sb.append("<button class=\"issues-tab\" role=\"tab\" aria-selected=\"false\" tabindex=\"-1\" data-tab=\"")
				.append(resolutionId).append("-broken-links\">Broken Links (").append(data.brokenLinks.size())
				.append(")</button>\n");
		sb.append("</div>\n");

		sb.append("<div class=\"issue-section grammar-issue-section\" id=\"").append(resolutionId)
				.append("-grammar-issues\" role=\"tabpanel\" tabindex=\"0\">\n");
		sb.append("<h3>⚠ Spelling Issues</h3>\n");
		if (data.grammarIssues.isEmpty()) {
			sb.append("<p>No spelling errors found.</p>\n");
		} else {
			for (GrammarIssue g : data.grammarIssues) {
				sb.append("<div class=\"grammar-issue-card\">\n");
				sb.append("<div class=\"grammar-issue-header\">\n");
				sb.append("<h4>").append(escapeHtml(g.elementType)).append("</h4>\n");
				sb.append("<span class=\"grammar-error-badge\">SPELLING ERROR</span>\n");
				sb.append("</div>\n");
				sb.append("<div class=\"grammar-issue-field\">\n");
				sb.append("<strong>Error Text:</strong>\n");
				sb.append("<div class=\"grammar-error-text\">").append(escapeHtml(g.context)).append("</div>\n");
				sb.append("</div>\n");
				sb.append("<div class=\"grammar-issue-field\">\n");
				sb.append("<strong>Suggested Correction:</strong>\n");
				sb.append("<div class=\"grammar-suggestion-text\">").append(escapeHtml(g.suggestion))
						.append("</div>\n");
				sb.append("</div>\n");
				sb.append("<div class=\"grammar-issue-field\">\n");
				sb.append("<strong>Issue Description:</strong>\n");
				sb.append("<div class=\"grammar-message-text\">").append(escapeHtml(g.message)).append("</div>\n");
				sb.append("</div>\n");
				sb.append("<div class=\"grammar-issue-field\">\n");
				sb.append("<strong>Full Text Context:</strong>\n");
				sb.append("<textarea readonly rows=\"3\" class=\"grammar-context-textarea\">")
						.append(escapeHtml(g.fullText)).append("</textarea>\n");
				sb.append("</div>\n");
				sb.append("</div>\n");
			}
		}
		sb.append("</div>\n");

		sb.append("<div class=\"issue-section broken-issue-section\" id=\"").append(resolutionId)
				.append("-broken-links\" role=\"tabpanel\" tabindex=\"0\" hidden>\n");
		sb.append("<h3>🔗 Broken Links</h3>\n");
		sb.append("<table>\n");
		sb.append(
				"<thead>\n<tr>\n<th>Link Text</th>\n<th>Page URL</th>\n<th>Broken URL</th>\n<th>Status</th>\n</tr>\n</thead>\n");
		sb.append("<tbody>\n");
		if (data.brokenLinks.isEmpty()) {
			sb.append("<tr><td colspan=\"4\">No broken links found.</td></tr>\n");
		} else {
			for (BrokenLink b : data.brokenLinks) {
				LOGGER.warning("Broken link detected: " + b.url + ", Status: " + b.status);
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
	}

	private void appendModernCSS(StringBuilder sb) {
		sb.append(
				"@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap');\n");
		sb.append(":root {\n");
		sb.append("  --bg-primary: #f8f9fa;\n");
		sb.append("  --bg-secondary: #ffffff;\n");
		sb.append("  --bg-card: #ffffff;\n");
		sb.append("  --bg-surface: #f1f3f5;\n");
		sb.append("  --text-primary: #1e293b;\n");
		sb.append("  --text-secondary: #64748b;\n");
		sb.append("  --text-muted: #94a3b8;\n");
		sb.append("  --border-color: #e2e8f0;\n");
		sb.append("  --border-hover: #cbd5e1;\n");
		sb.append("  --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);\n");
		sb.append("  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);\n");
		sb.append("  --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);\n");
		sb.append("  --accent-primary: #2563eb;\n");
		sb.append("  --accent-hover: #1d4ed8;\n");
		sb.append("  --accent-light: #dbeafe;\n");
		sb.append("  --success: #059669;\n");
		sb.append("  --success-light: #d1fae5;\n");
		sb.append("  --warning: #d97706;\n");
		sb.append("  --warning-light: #fef3c7;\n");
		sb.append("  --error: #dc2626;\n");
		sb.append("  --error-light: #fee2e2;\n");
		sb.append("  --info: #0284c7;\n");
		sb.append("  --info-light: #e0f2fe;\n");
		sb.append("}\n");
		sb.append("[data-theme=\"dark\"] {\n");
		sb.append("  --bg-primary: #0f172a;\n");
		sb.append("  --bg-secondary: #1e293b;\n");
		sb.append("  --bg-card: #1e293b;\n");
		sb.append("  --bg-surface: #334155;\n");
		sb.append("  --text-primary: #f1f5f9;\n");
		sb.append("  --text-secondary: #cbd5e1;\n");
		sb.append("  --text-muted: #94a3b8;\n");
		sb.append("  --border-color: #334155;\n");
		sb.append("  --border-hover: #475569;\n");
		sb.append("  --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.3);\n");
		sb.append("  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.4), 0 2px 4px -1px rgba(0, 0, 0, 0.3);\n");
		sb.append("  --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.5), 0 4px 6px -2px rgba(0, 0, 0, 0.4);\n");
		sb.append("  --accent-primary: #3b82f6;\n");
		sb.append("  --accent-hover: #60a5fa;\n");
		sb.append("  --accent-light: #1e3a8a;\n");
		sb.append("}\n");
		sb.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
		sb.append("* { animation: none !important; transition: none !important; }\n");
		sb.append(
				"body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: var(--bg-primary); color: var(--text-primary); line-height: 1.6; min-height: 100vh; padding: 24px; font-size: 15px; }\n");
		sb.append(".dashboard-container { max-width: 1600px; margin: 0 auto; }\n");
		sb.append(
				"header { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 12px; padding: 28px 32px; margin-bottom: 32px; display: flex; align-items: center; justify-content: space-between; box-shadow: var(--shadow-sm); }\n");
		sb.append("header svg { width: 32px; height: 32px; fill: var(--accent-primary); margin-right: 16px; }\n");
		sb.append(
				"header h1 { font-size: 28px; font-weight: 700; color: var(--text-primary); letter-spacing: -0.5px; }\n");
		sb.append(
				"header span { font-size: 14px; color: var(--text-secondary); font-weight: 400; margin-top: 4px; display: block; }\n");
		sb.append(
				".theme-toggle { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 10px 16px; cursor: pointer; font-size: 14px; font-weight: 500; color: var(--text-primary); display: flex; align-items: center; gap: 8px; transition: all 0.2s; }\n");
		sb.append(".theme-toggle:hover { background: var(--border-hover); border-color: var(--border-hover); }\n");
		sb.append(".analyze-bar { display: flex; gap: 12px; margin-bottom: 32px; }\n");
		sb.append(
				"input[type=\"url\"] { flex: 1; padding: 14px 20px; border-radius: 8px; border: 1px solid var(--border-color); background: var(--bg-secondary); font-size: 15px; color: var(--text-primary); font-family: 'Inter', sans-serif; transition: all 0.2s; }\n");
		sb.append(
				"input[type=\"url\"]:focus { outline: none; border-color: var(--accent-primary); box-shadow: 0 0 0 3px var(--accent-light); }\n");
		sb.append(
				"button { background: var(--accent-primary); border: none; padding: 14px 28px; border-radius: 8px; color: white; font-weight: 600; font-size: 15px; cursor: pointer; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(
				"button:hover { background: var(--accent-hover); box-shadow: var(--shadow-md); transform: translateY(-1px); }\n");
		sb.append(
				".dashboard-section { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 12px; padding: 32px; margin-bottom: 32px; box-shadow: var(--shadow-sm); }\n");
		sb.append(
				".dashboard-header { font-size: 24px; font-weight: 700; color: var(--text-primary); margin-bottom: 8px; letter-spacing: -0.3px; }\n");
		sb.append(
				".dashboard-subheader { font-size: 14px; color: var(--text-secondary); font-weight: 400; margin-bottom: 24px; }\n");
		sb.append(".resolution-container { margin-bottom: 32px; }\n");
		sb.append(
				".resolution-header { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 20px 24px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(".resolution-header:hover { border-color: var(--border-hover); box-shadow: var(--shadow-md); }\n");
		sb.append(
				".resolution-header.active { border-color: var(--accent-primary); background: var(--accent-light); }\n");
		sb.append(
				".resolution-header h3 { font-size: 18px; font-weight: 600; color: var(--text-primary); margin: 0; }\n");
		sb.append(
				".resolution-info { font-size: 13px; color: var(--text-secondary); font-weight: 400; margin-top: 4px; }\n");
		sb.append(".toggle-icon { font-size: 18px; color: var(--accent-primary); transition: transform 0.2s; }\n");
		sb.append(".resolution-header.active .toggle-icon { transform: rotate(180deg); }\n");
		sb.append(
				".resolution-content { display: none; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 10px; padding: 32px; box-shadow: var(--shadow-sm); margin-top: 16px; }\n");
		sb.append(".resolution-content.active { display: block; }\n");
		sb.append(
				".tabs { display: flex; gap: 4px; border-bottom: 2px solid var(--border-color); margin-bottom: 32px; padding-bottom: 0; overflow-x: auto; scrollbar-width: none; }\n");
		sb.append(".tabs::-webkit-scrollbar { display: none; }\n");
		sb.append(
				".tab { font-weight: 500; font-size: 15px; color: var(--text-secondary); padding: 14px 20px; cursor: pointer; border-bottom: 3px solid transparent; position: relative; white-space: nowrap; transition: all 0.2s; }\n");
		sb.append(".tab:hover { color: var(--accent-primary); background: var(--bg-surface); }\n");
		sb.append(
				".tab.active { color: var(--accent-primary); border-bottom-color: var(--accent-primary); font-weight: 600; background: var(--accent-light); }\n");
		sb.append(".tab-content { display: none; }\n");
		sb.append(".tab-content.active { display: block; }\n");
		sb.append(
				".seo-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; margin-bottom: 40px; }\n");
		sb.append(
				".seo-card { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 24px; min-height: 120px; display: flex; flex-direction: column; justify-content: center; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(
				".seo-card:hover { box-shadow: var(--shadow-md); border-color: var(--accent-primary); transform: translateY(-2px); }\n");
		sb.append(
				".seo-card h3 { font-size: 12px; font-weight: 600; color: var(--accent-primary); margin-bottom: 12px; text-transform: uppercase; letter-spacing: 0.8px; }\n");
		sb.append(
				".seo-card p { font-size: 15px; line-height: 1.6; color: var(--text-primary); word-break: break-word; }\n");
		sb.append(".seo-card .ok { color: var(--success); font-weight: 600; margin-left: 8px; font-size: 16px; }\n");
		sb.append(
				".seo-card .warning { color: var(--warning); font-weight: 500; display: block; margin-top: 10px; font-size: 13px; padding: 8px 12px; background: var(--warning-light); border-radius: 6px; }\n");
		sb.append(
				".seo-score-container { text-align: center; margin-bottom: 48px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 12px; padding: 48px 32px; box-shadow: var(--shadow-sm); }\n");
		sb.append(
				".score-circle { width: 180px; height: 180px; margin: 0 auto 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 52px; font-weight: 700; color: var(--accent-primary); position: relative; background: var(--accent-light); border: 6px solid var(--accent-primary); }\n");
		sb.append(
				".score-circle span { font-weight: 400; font-size: 22px; color: var(--text-secondary); margin-left: 4px; }\n");
		sb.append(
				".score-metrics { display: flex; justify-content: center; gap: 20px; flex-wrap: wrap; font-size: 14px; font-weight: 500; }\n");
		sb.append(
				".score-box { border-radius: 10px; padding: 20px 24px; min-width: 160px; text-align: center; background: var(--bg-secondary); border: 1px solid var(--border-color); transition: all 0.2s; }\n");
		sb.append(".score-box:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }\n");
		sb.append(
				".score-headings { background: var(--info-light); border-color: var(--info); color: var(--info); }\n");
		sb.append(
				".score-content { background: var(--success-light); border-color: var(--success); color: var(--success); }\n");
		sb.append(
				".score-link { background: var(--warning-light); border-color: var(--warning); color: var(--warning); }\n");
		sb.append(
				".score-box small { display: block; margin-top: 8px; font-weight: 400; font-size: 12px; opacity: 0.8; }\n");
		sb.append(".heading-structure { display: flex; gap: 16px; margin-bottom: 32px; flex-wrap: wrap; }\n");
		sb.append(
				".heading-box { background: var(--bg-secondary); border: 1px solid var(--border-color); flex: 1 1 100px; border-radius: 10px; text-align: center; padding: 24px 16px; color: var(--text-primary); font-weight: 500; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(
				".heading-box:hover { box-shadow: var(--shadow-md); border-color: var(--accent-primary); transform: translateY(-2px); }\n");
		sb.append(
				".heading-box strong { display: block; font-size: 32px; font-weight: 700; margin-bottom: 8px; color: var(--accent-primary); }\n");
		sb.append(
				".heading-subtabs { display: flex; border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; margin-bottom: 24px; background: var(--bg-surface); }\n");
		sb.append(
				".heading-subtab { flex: 1; padding: 14px 0; background: transparent; border: none; cursor: pointer; font-weight: 500; font-size: 14px; color: var(--text-secondary); text-align: center; position: relative; transition: all 0.2s; }\n");
		sb.append(".heading-subtab:hover { background: var(--bg-secondary); color: var(--accent-primary); }\n");
		sb.append(
				".heading-subtab.active { background: var(--bg-secondary); color: var(--accent-primary); font-weight: 600; }\n");
		sb.append(
				".heading-subtab.active::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 3px; background: var(--accent-primary); }\n");
		sb.append(
				".heading-detail { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 28px; font-size: 14px; color: var(--text-primary); display: none; }\n");
		sb.append(".heading-detail.active { display: block; }\n");
		sb.append(
				".heading-detail-box { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 10px; padding: 20px; margin-bottom: 16px; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(".heading-detail-box:hover { box-shadow: var(--shadow-md); border-color: var(--accent-primary); }\n");
		sb.append(
				".heading-detail h4 { margin-top: 0; margin-bottom: 16px; font-weight: 600; font-size: 18px; color: var(--accent-primary); }\n");
		sb.append(
				".heading-properties { display: flex; flex-wrap: wrap; gap: 20px; list-style: none; padding: 0; margin: 0; }\n");
		sb.append(
				".heading-properties li { display: flex; align-items: center; font-size: 14px; color: var(--text-primary); }\n");
		sb.append(
				".heading-properties li::before { content: '▸'; margin-right: 8px; color: var(--accent-primary); font-weight: 600; }\n");
		sb.append(
				".heading-properties li strong { font-weight: 600; margin-right: 6px; color: var(--accent-primary); }\n");
		sb.append(
				".color-box { display: inline-block; width: 20px; height: 20px; border-radius: 6px; border: 2px solid var(--border-color); vertical-align: middle; margin-right: 8px; box-shadow: var(--shadow-sm); }\n");
		sb.append(
				".button-color-box { width: 24px; height: 24px; margin-right: 8px; border-radius: 6px; border: 2px solid var(--border-color); box-shadow: var(--shadow-sm); }\n");
		sb.append(
				".link-url { color: var(--accent-primary); font-weight: 500; text-decoration: none; transition: color 0.2s; word-break: break-word; overflow-wrap: break-word; display: inline-block; max-width: 100%; }\n");
		sb.append(".link-url:hover { text-decoration: underline; color: var(--accent-hover); }\n");
		sb.append(".element-url-row { margin-top: 20px; }\n");
		sb.append(".elements-overview { display: flex; gap: 16px; margin-bottom: 32px; flex-wrap: wrap; }\n");
		sb.append(
				".element-box { background: var(--accent-primary); color: white; font-weight: 600; font-size: 16px; flex: 1 1 200px; border-radius: 10px; padding: 24px; text-align: center; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(
				".element-box:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); background: var(--accent-hover); }\n");
		sb.append(
				".elements-subtabs { display: flex; border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; margin-bottom: 24px; background: var(--bg-surface); }\n");
		sb.append(
				".elements-subtabs button { flex: 1; padding: 14px 0; background: transparent; border: none; cursor: pointer; font-weight: 500; font-size: 14px; color: var(--text-secondary); border-radius: 0; box-shadow: none; transition: all 0.2s; }\n");
		sb.append(
				".elements-subtabs button:hover { background: var(--bg-secondary); color: var(--accent-primary); }\n");
		sb.append(
				".elements-subtabs button.active { background: var(--bg-secondary); color: var(--accent-primary); font-weight: 600; position: relative; }\n");
		sb.append(
				".elements-subtabs button.active::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 3px; background: var(--accent-primary); }\n");
		sb.append(
				".element-details { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 28px; font-size: 14px; color: var(--text-primary); }\n");
		sb.append(
				".element-header { font-weight: 600; font-size: 20px; margin-bottom: 24px; color: var(--accent-primary); }\n");
		sb.append(
				"details { border: 1px solid var(--border-color); border-radius: 10px; padding: 20px; margin-bottom: 16px; background: var(--bg-secondary); transition: all 0.2s; }\n");
		sb.append("details:hover { border-color: var(--accent-primary); box-shadow: var(--shadow-sm); }\n");
		sb.append(
				"summary { font-weight: 600; font-size: 16px; outline: none; user-select: none; list-style: none; position: relative; cursor: pointer; color: var(--text-primary); padding-right: 32px; }\n");
		sb.append("summary::-webkit-details-marker { display: none; }\n");
		sb.append(
				"summary::after { content: \"▼\"; position: absolute; right: 0; top: 50%; transform: translateY(-50%); font-size: 12px; color: var(--accent-primary); }\n");
		sb.append("details[open] summary::after { transform: translateY(-50%) rotate(180deg); }\n");
		sb.append(".element-row { display: flex; gap: 24px; margin-top: 20px; flex-wrap: wrap; }\n");
		sb.append(".element-row > div { flex: 1; min-width: 180px; max-width: 100%; margin-bottom: 12px; overflow-wrap: break-word; word-break: break-word; }\n");
		sb.append(
				".element-row > div strong { font-weight: 700; color: var(--accent-primary); display: block; margin-bottom: 6px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; }\n");
		sb.append(
				".element-row > div span.value { font-weight: 400; color: var(--text-primary); display: block; font-size: 14px; word-break: break-word; overflow-wrap: break-word; }\n");
		sb.append(
				".color-pair-row { display: flex; align-items: center; gap: 32px; margin-top: 20px; flex-wrap: wrap; }\n");
		sb.append(".color-pair-row > div { display: flex; flex-direction: column; }\n");
		sb.append(
				".paragraph-card { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 24px; margin-bottom: 20px; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(".paragraph-card:hover { box-shadow: var(--shadow-md); border-color: var(--accent-primary); }\n");
		sb.append(
				".paragraph-card textarea { width: 100%; height: 120px; font-size: 14px; font-family: 'Inter', sans-serif; resize: vertical; padding: 14px; border: 1px solid var(--border-color); border-radius: 8px; color: var(--text-primary); background: var(--bg-surface); margin-bottom: 16px; overflow-y: auto; box-sizing: border-box; transition: all 0.2s; }\n");
		sb.append(
				".paragraph-card textarea:focus { outline: none; border-color: var(--accent-primary); box-shadow: 0 0 0 3px var(--accent-light); }\n");
		sb.append(
				".paragraph-card .details { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 16px; }\n");
		sb.append(
				".paragraph-card .details div { padding: 16px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; }\n");
		sb.append(
				".paragraph-card .details div strong { display: block; margin-bottom: 8px; color: var(--accent-primary); font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600; }\n");
		sb.append(".paragraph-card .details div span { color: var(--text-primary); font-size: 14px; }\n");
		sb.append(
				".paragraph-card .heading-text-display { font-size: 18px; font-weight: 600; color: var(--accent-primary); margin-bottom: 16px; padding: 14px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; word-break: break-word; }\n");
		sb.append(
				".paragraph-card .url-box { width: 100%; margin-top: 16px; padding: 16px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; }\n");
		sb.append(
				".paragraph-card .url-box strong { display: block; margin-bottom: 8px; color: var(--accent-primary); font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600; }\n");
		sb.append(
				".paragraph-card .url-box span { display: block; word-break: break-all; overflow-wrap: break-word; color: var(--text-primary); font-size: 14px; }\n");
		sb.append(".issues-overview { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 32px; }\n");
		sb.append(
				".issue-box { flex: 1 1 200px; text-align: center; font-weight: 600; font-size: 16px; border-radius: 10px; padding: 28px 24px; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(".issue-box:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }\n");
		sb.append(
				".issue-box.total { background: var(--bg-surface); color: var(--text-primary); border: 1px solid var(--border-color); }\n");
		sb.append(
				".issue-box.grammar { background: var(--warning-light); color: var(--warning); border: 1px solid var(--warning); }\n");
		sb.append(
				".issue-box.broken { background: var(--error-light); color: var(--error); border: 1px solid var(--error); }\n");
		sb.append(
				".issues-subtabs { display: flex; border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; max-width: 400px; margin-bottom: 24px; background: var(--bg-surface); }\n");
		sb.append(
				".issues-subtabs button { flex: 1; padding: 14px 0; background: transparent; border: none; cursor: pointer; font-weight: 500; font-size: 14px; color: var(--text-secondary); border-radius: 0; box-shadow: none; transition: all 0.2s; }\n");
		sb.append(".issues-subtabs button:hover { background: var(--bg-secondary); color: var(--error); }\n");
		sb.append(
				".issues-subtabs button.active { background: var(--bg-secondary); color: var(--error); font-weight: 600; position: relative; }\n");
		sb.append(
				".issues-subtabs button.active::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 3px; background: var(--error); }\n");
		sb.append(
				".issue-section { border-radius: 10px; padding: 32px; font-size: 14px; border: 1px solid var(--border-color); background: var(--bg-secondary); }\n");
		sb.append(".grammar-issue-section { border-color: var(--warning); background: var(--warning-light); }\n");
		sb.append(".broken-issue-section { border-color: var(--error); background: var(--error-light); }\n");
		sb.append(
				".issue-section h3 { font-size: 22px; font-weight: 700; margin-bottom: 24px; color: var(--text-primary); }\n");
		sb.append(
				".grammar-issue-card { background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 24px; margin-bottom: 20px; box-shadow: var(--shadow-sm); transition: all 0.2s; }\n");
		sb.append(".grammar-issue-card:hover { box-shadow: var(--shadow-md); border-color: var(--warning); }\n");
		sb.append(
				".grammar-issue-card h4 { margin: 0 0 16px 0; color: var(--warning); font-size: 16px; font-weight: 600; }\n");
		sb.append(".grammar-issue-card > div { margin-bottom: 16px; }\n");
		sb.append(
				".grammar-issue-card strong { color: var(--text-primary); display: block; margin-bottom: 8px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600; }\n");
		sb.append(
				".grammar-issue-card textarea { width: 100%; padding: 12px; border: 1px solid var(--border-color); border-radius: 8px; font-family: 'Inter', sans-serif; font-size: 14px; resize: vertical; background: var(--bg-surface); color: var(--text-primary); }\n");
		sb.append(
				".grammar-issue-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }\n");
		sb.append(
				".grammar-error-badge { background: var(--warning); color: white; padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 600; }\n");
		sb.append(".grammar-issue-field { margin-bottom: 16px; }\n");
		sb.append(
				".grammar-issue-field strong { color: var(--text-primary); display: block; margin-bottom: 8px; font-size: 13px; text-transform: uppercase; letter-spacing: 0.5px; font-weight: 600; }\n");
		sb.append(
				".grammar-error-text { background: var(--bg-surface); padding: 12px; border-radius: 8px; border-left: 3px solid var(--warning); font-family: 'Courier New', monospace; font-size: 14px; color: var(--text-primary); }\n");
		sb.append(
				".grammar-suggestion-text { background: var(--success-light); padding: 12px; border-radius: 8px; border-left: 3px solid var(--success); font-family: 'Courier New', monospace; font-size: 14px; color: var(--text-primary); }\n");
		sb.append(
				".grammar-message-text { background: var(--info-light); padding: 12px; border-radius: 8px; border-left: 3px solid var(--info); font-size: 14px; color: var(--text-primary); line-height: 1.5; }\n");
		sb.append(
				".grammar-context-textarea { width: 100%; padding: 12px; border: 1px solid var(--border-color); border-radius: 8px; font-family: 'Inter', sans-serif; font-size: 14px; resize: vertical; background: var(--bg-surface); color: var(--text-primary); margin-top: 8px; }\n");
		sb.append(
				"table { width: 100%; border-collapse: collapse; font-size: 14px; background: var(--bg-secondary); border-radius: 10px; overflow: hidden; box-shadow: var(--shadow-sm); border: 1px solid var(--border-color); }\n");
		sb.append("thead tr { background: var(--error-light); color: var(--error); font-weight: 600; }\n");
		sb.append("th, td { padding: 16px; border-bottom: 1px solid var(--border-color); text-align: left; }\n");
		sb.append("tbody tr:hover { background: var(--bg-surface); }\n");
		sb.append(
				"a.link-table { color: var(--accent-primary); text-decoration: none; font-weight: 500; transition: color 0.2s; }\n");
		sb.append("a.link-table:hover { text-decoration: underline; color: var(--accent-hover); }\n");
		sb.append("td.status { color: var(--error); font-weight: 600; }\n");
		sb.append(".screenshots-wrapper { position: relative; width: 100%; }\n");
		sb.append(
				".screenshot-wrapper { display: flex; flex-direction: column; height: calc(100vh - 280px); min-height: 600px; max-height: 900px; margin-bottom: 10px; }\n");
		sb.append(
				".screenshot-viewer-container { display: flex; gap: 24px; flex: 1 1 auto; min-height: 0; overflow: hidden; }\n");
		sb.append(
				".screenshot-viewer { flex: 1 1 60%; min-width: 300px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 20px; box-shadow: var(--shadow-sm); display: flex; align-items: center; justify-content: center; overflow: hidden; }\n");
		sb.append(
				".screenshot-viewer img { max-width: 100%; max-height: 100%; width: auto; height: auto; object-fit: contain; border-radius: 8px; border: 2px solid var(--border-color); box-shadow: var(--shadow-md); }\n");
		sb.append(
				".element-details-panel { flex: 1 1 35%; min-width: 280px; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: 10px; padding: 24px; box-shadow: var(--shadow-sm); overflow-y: auto; display: flex; flex-direction: column; min-height: 0; }\n");
		sb.append(".element-details-panel::-webkit-scrollbar { width: 8px; }\n");
		sb.append(
				".element-details-panel::-webkit-scrollbar-track { background: var(--bg-surface); border-radius: 4px; }\n");
		sb.append(
				".element-details-panel::-webkit-scrollbar-thumb { background: var(--border-color); border-radius: 4px; }\n");
		sb.append(".element-details-panel::-webkit-scrollbar-thumb:hover { background: var(--border-hover); }\n");
		sb.append(
				".element-details-header { font-size: 18px; font-weight: 600; color: var(--accent-primary); margin-bottom: 20px; padding-bottom: 12px; border-bottom: 2px solid var(--border-color); display: flex; align-items: center; gap: 12px; flex-shrink: 0; }\n");
		sb.append(
				".element-color-indicator { width: 24px; height: 24px; border-radius: 6px; border: 2px solid var(--border-color); box-shadow: var(--shadow-sm); }\n");
		sb.append(".element-details-section { margin-bottom: 24px; flex-shrink: 0; }\n");
		sb.append(
				".element-details-section h4 { font-size: 14px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.8px; margin-bottom: 12px; }\n");
		sb.append(
				".element-details-section .property-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--border-color); }\n");
		sb.append(".element-details-section .property-row:last-child { border-bottom: none; }\n");
		sb.append(
				".element-details-section .property-label { font-weight: 600; color: var(--text-primary); font-size: 13px; text-transform:capitalize; }\n");
		sb.append(
				".element-details-section .property-value { color: var(--text-secondary); font-size: 13px; word-break: break-word; text-align: right; max-width: 60%; }\n");
		sb.append(
				".element-text-preview { background: var(--bg-surface); padding: 12px; border-radius: 8px; border-left: 3px solid var(--accent-primary); margin-top: 12px; font-size: 14px; color: var(--text-primary); line-height: 1.5; max-height: 150px; overflow-y: auto; }\n");
		sb.append(
				".screenshot-navigation { display: flex; justify-content: right; align-items: center; padding: 12px 0; border-top: 1px solid var(--border-color); background: var(--bg-secondary); position: sticky; bottom: 0; z-index: 10; flex-shrink: 0; margin-top: auto; }\n");
		sb.append(
				".screenshot-nav-button { background: var(--bg-surface); border: 1px solid var(--border-color); padding: 8px 16px; border-radius: 6px; cursor: pointer; font-size: 15px; font-weight: 700; color: var(--text-primary); transition: all 0.2s; margin-right: 20px;}\n");
		sb.append(
				".screenshot-nav-button:hover { background: var(--accent-primary); color: white; border-color: var(--accent-primary); }\n");
		sb.append(".screenshot-nav-button:disabled { opacity: 0.5; cursor: not-allowed; }\n");
		sb.append(
				".screenshot-counter { font-size: 13px; color: var(--text-secondary); font-weight: 500; margin: 0 15px; }\n");
		sb.append(
				".screenshot-section-title { font-size: 20px; font-weight: 600; color: var(--accent-primary); margin-bottom: 20px; padding-bottom: 12px; border-bottom: 2px solid var(--border-color); }\n");
		sb.append(".screenshot-element-group { margin-bottom: 40px; }\n");
		sb.append(
				".screenshot-element-group-title { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 16px; text-transform: capitalize; }\n");
		sb.append(
				"@media (max-width: 768px) { body { padding: 16px; } header { flex-direction: column; align-items: flex-start; gap: 16px; padding: 20px 24px; } .dashboard-container { max-width: 100%; } .tabs { gap: 4px; } .tab { padding: 12px 16px; font-size: 14px; } .seo-grid { grid-template-columns: 1fr; gap: 16px; } .score-circle { width: 160px; height: 160px; font-size: 48px; } .score-metrics { flex-direction: column; align-items: center; gap: 16px; } .heading-structure { gap: 12px; } .heading-box { flex: 1 1 calc(50% - 6px); } .elements-overview { flex-direction: column; gap: 12px; } .element-box { flex: 1 1 100%; } .issues-overview { flex-direction: column; gap: 12px; } .issue-box { flex: 1 1 100%; } .dashboard-section { padding: 24px; } .resolution-content { padding: 24px; } .screenshot-wrapper { height: calc(100vh - 200px); min-height: 500px; max-height: 800px; } .screenshot-viewer-container { flex-direction: column; } .screenshot-viewer, .element-details-panel { flex: 1 1 100%; } }\n");
	}

	private void generateDashboardHTML() throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
		sb.append("<meta charset=\"UTF-8\" />\n");
		sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n");
		sb.append("<title>WebQA Dashboard</title>\n");
		sb.append("<style>\n");
		appendModernCSS(sb);
		sb.append("</style>\n</head>\n<body>\n");
		sb.append("<div class=\"dashboard-container\">\n");

		sb.append("<header aria-label=\"WebQA Dashboard header\">");
		sb.append("<div style=\"display: flex; align-items: center;\">");
		sb.append(
				"<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\" focusable=\"false\"><path d=\"M3 13h2v-2H3v2zm3 0h2v-2H6v2zm3 0h2v-2h-2v2zM3 18h2v-2H3v2zm3 0h2v-2H6v2zm3 0h2v-2h-2v2zM3 8h2V6H3v2zm3 0h2V6H6v2zm3 0h2V6h-2v2zm8 2v7a2 2 0 11-4 0v-7h-2v7a4 4 0 008 0v-7h-2zm0-6v2h2V4h-2zm0 8v-2h2v2h--2z\"/></svg>\n");
		sb.append("<div>");
		sb.append("<h1>WebQA Dashboard</h1>\n");
		sb.append("<span>Analyze website structure and SEO</span>\n");
		sb.append("</div>");
		sb.append("</div>");
		sb.append(
				"<button class=\"theme-toggle\" onclick=\"document.documentElement.setAttribute('data-theme', document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark'); localStorage.setItem('theme', document.documentElement.getAttribute('data-theme'));\">🌓 Theme</button>\n");
		sb.append("</header>\n");

		WebsiteData data = websiteDataList.get(websiteDataList.size() - 1);

//		sb.append("<div class=\"analyze-bar\">");
//		sb.append("<input type=\"url\" placeholder=\"Enter URL to analyze\" value=\"").append(escapeHtml(data.url))
//				.append("\" aria-label=\"Enter URL to analyze\" readonly />\n");
//		sb.append("<button aria-label=\"Analyze website\">Analyze</button>\n");
//		sb.append("</div>\n");

		sb.append("<div class=\"dashboard-section\">");
		String firstUrl = resolutionDataMap.isEmpty() ? data.url : resolutionDataMap.values().iterator().next().url;
		sb.append("<div class=\"dashboard-header\" id=\"dashboard-title\">Dashboard for: <a href=\"")
				.append(escapeHtml(firstUrl))
				.append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"link-url\" style=\"color: inherit; text-decoration: underline;\">")
				.append(escapeHtml(firstUrl)).append("</a></div>\n");
		int resolutionCount = resolutionDataMap.isEmpty() ? 1 : resolutionDataMap.size();
		String subheaderText = resolutionCount == 1 ? "Analyzed website structure, content, and SEO elements"
				: "Analyzed website structure, content, and SEO elements across " + resolutionCount
						+ " screen resolutions";

		// Add current date and time
		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
		String reportDateTime = now.format(formatter);

		sb.append("<div class=\"dashboard-subheader\">").append(subheaderText).append(" | Generated on: <strong>")
				.append(reportDateTime).append("</strong>").append("</div>\n");
		sb.append("</div>\n");

		// Generate resolution content for all resolutions (single or multiple)
		if (!resolutionDataMap.isEmpty()) {
			int index = 0;
			for (Map.Entry<String, WebsiteData> entry : resolutionDataMap.entrySet()) {
				String resolutionName = entry.getKey();
				WebsiteData resolutionData = entry.getValue();
				ScreenSize screenSize = SCREEN_SIZES.stream().filter(s -> s.name.equals(resolutionName)).findFirst()
						.orElse(null);

				String description = screenSize != null ? screenSize.description : "";
				boolean isFirst = index == 0;
				boolean isMultiple = resolutionDataMap.size() > 1;

				// Only show resolution selector if multiple resolutions exist
				if (isMultiple) {
					sb.append("<div class=\"resolution-container\">\n");
					sb.append("<div class=\"resolution-header").append(isFirst ? " active" : "")
							.append("\" onclick=\"toggleResolution('resolution-").append(index).append("', this)\">\n");
					sb.append("<div>\n");
					sb.append("<h3>").append(escapeHtml(resolutionName)).append("</h3>\n");
					sb.append("<div class=\"resolution-info\">").append(escapeHtml(description)).append("</div>\n");
					sb.append("</div>\n");
					sb.append("<span class=\"toggle-icon\">▼</span>\n");
					sb.append("</div>\n");
				} else {
					// Single resolution - show header without toggle
					sb.append("<div class=\"resolution-container\">\n");
					sb.append("<div class=\"resolution-header active\">\n");
					sb.append("<div>\n");
					sb.append("<h3>").append(escapeHtml(resolutionName)).append("</h3>\n");
					sb.append("<div class=\"resolution-info\">").append(escapeHtml(description)).append("</div>\n");
					sb.append("</div>\n");
					sb.append("</div>\n");
				}

				sb.append("<div class=\"resolution-content").append(isFirst ? " active" : "")
						.append("\" id=\"resolution-").append(index).append("\">\n");

				// Generate content for this resolution
				generateResolutionContent(sb, resolutionData, resolutionName);

				sb.append("</div>\n"); // Close resolution-content
				sb.append("</div>\n"); // Close resolution-container
				index++;
			}
		} else {
			// Fallback: if resolutionDataMap is empty, use the last websiteData
			// This should not happen in normal flow, but handle it gracefully
			String resolutionName = "1920 × 1080";
			if (!websiteDataList.isEmpty()) {
				WebsiteData lastData = websiteDataList.get(websiteDataList.size() - 1);
				generateResolutionContent(sb, lastData, resolutionName);
			}
		}

		sb.append("<script>\n");
		sb.append("// Theme Toggle\n");
		sb.append("function initThemeToggle() {\n");
		sb.append("  const themeToggle = document.querySelector('.theme-toggle');\n");
		sb.append("  if (!themeToggle) return;\n");
		sb.append("  const currentTheme = localStorage.getItem('theme') || 'light';\n");
		sb.append("  document.documentElement.setAttribute('data-theme', currentTheme);\n");
		sb.append("  themeToggle.addEventListener('click', () => {\n");
		sb.append("    const currentTheme = document.documentElement.getAttribute('data-theme');\n");
		sb.append("    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';\n");
		sb.append("    document.documentElement.setAttribute('data-theme', newTheme);\n");
		sb.append("    localStorage.setItem('theme', newTheme);\n");
		sb.append("  });\n");
		sb.append("}\n");
		sb.append("// Resolution Toggle\n");
		sb.append("function toggleResolution(resolutionId, headerElement) {\n");
		sb.append("  const content = document.getElementById(resolutionId);\n");
		sb.append("  const isActive = content.classList.contains('active');\n");
		sb.append(
				"  document.querySelectorAll('.resolution-content').forEach(el => { el.classList.remove('active'); });\n");
		sb.append(
				"  document.querySelectorAll('.resolution-header').forEach(el => { el.classList.remove('active'); });\n");
		sb.append("  if (!isActive) {\n");
		sb.append("    content.classList.add('active');\n");
		sb.append("    headerElement.classList.add('active');\n");
		sb.append("    let activeTab = content.querySelector('.tab.active');\n");
		sb.append("    if(!activeTab) {\n");
		sb.append("      const firstMainTab = content.querySelector('.tab');\n");
		sb.append("      if(firstMainTab) {\n");
		sb.append(
				"        content.querySelectorAll('.tab').forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); t.tabIndex = -1; });\n");
		sb.append(
				"        firstMainTab.classList.add('active'); firstMainTab.setAttribute('aria-selected', 'true'); firstMainTab.tabIndex = 0; activeTab = firstMainTab;\n");
		sb.append("        const targetTabContent = document.getElementById(firstMainTab.dataset.tab);\n");
		sb.append("        if(targetTabContent) {\n");
		sb.append(
				"          content.querySelectorAll('.tab-content').forEach(tc => { tc.classList.remove('active'); tc.setAttribute('aria-hidden', 'true'); tc.tabIndex = -1; });\n");
		sb.append(
				"          targetTabContent.classList.add('active'); targetTabContent.setAttribute('aria-hidden', 'false'); targetTabContent.tabIndex = 0;\n");
		sb.append("        }\n");
		sb.append("      }\n");
		sb.append("    }\n");
		sb.append("    if(activeTab) {\n");
		sb.append("      const targetTabContent = document.getElementById(activeTab.dataset.tab);\n");
		sb.append("      if(targetTabContent) {\n");
		sb.append(
				"        const restored = restoreChildTabState(resolutionId, activeTab.dataset.tab, targetTabContent);\n");
		sb.append("        if (!restored) {\n");
		sb.append("          const firstHeadingTab = targetTabContent.querySelector('.heading-subtab');\n");
		sb.append(
				"          if(firstHeadingTab && !firstHeadingTab.classList.contains('active')) { firstHeadingTab.click(); }\n");
		sb.append("          const firstElemTab = targetTabContent.querySelector('.elements-tab');\n");
		sb.append(
				"          if(firstElemTab && !firstElemTab.classList.contains('active')) { firstElemTab.click(); }\n");
		sb.append("          const firstIssueTab = targetTabContent.querySelector('.issues-tab');\n");
		sb.append(
				"          if(firstIssueTab && !firstIssueTab.classList.contains('active')) { firstIssueTab.click(); }\n");
		sb.append("        }\n");
		sb.append("      }\n");
		sb.append("    }\n");
		sb.append("  }\n");
		sb.append("}\n");
		sb.append("// Tab State Persistence Functions\n");
		sb.append("function saveTabState(resolutionId, mainTabId, childTabId) {\n");
		sb.append("  const key = `tabState_${resolutionId}_${mainTabId}`;\n");
		sb.append("  if (childTabId) {\n");
		sb.append("    localStorage.setItem(key, childTabId);\n");
		sb.append("  }\n");
		sb.append("}\n");
		sb.append("function restoreChildTabState(resolutionId, mainTabId, tabContent) {\n");
		sb.append("  const key = `tabState_${resolutionId}_${mainTabId}`;\n");
		sb.append("  const savedChildTabId = localStorage.getItem(key);\n");
		sb.append("  if (savedChildTabId) {\n");
		sb.append("    // Try to restore heading subtab\n");
		sb.append(
				"    const headingTab = tabContent.querySelector(`.heading-subtab[data-tab='${savedChildTabId}']`);\n");
		sb.append("    if (headingTab) {\n");
		sb.append("      headingTab.click();\n");
		sb.append("      return true;\n");
		sb.append("    }\n");
		sb.append("    // Try to restore elements tab\n");
		sb.append("    const elemTab = tabContent.querySelector(`.elements-tab[data-tab='${savedChildTabId}']`);\n");
		sb.append("    if (elemTab) {\n");
		sb.append("      elemTab.click();\n");
		sb.append("      return true;\n");
		sb.append("    }\n");
		sb.append("    // Try to restore issues tab\n");
		sb.append("    const issueTab = tabContent.querySelector(`.issues-tab[data-tab='${savedChildTabId}']`);\n");
		sb.append("    if (issueTab) {\n");
		sb.append("      issueTab.click();\n");
		sb.append("      return true;\n");
		sb.append("    }\n");
		sb.append("  }\n");
		sb.append("  return false;\n");
		sb.append("}\n");
		sb.append("// Main Tab Click Handler\n");
		sb.append("function initMainTabs() {\n");
		sb.append("  const tabs = document.querySelectorAll('.tab');\n");
		sb.append("  const tabContents = document.querySelectorAll('.tab-content');\n");
		sb.append("  tabs.forEach(tab => {\n");
		sb.append("    tab.addEventListener('click', () => {\n");
		sb.append(
				"      const resolutionContent = tab.closest('.resolution-content') || document.querySelector('.resolution-content');\n");
		sb.append("      const resolutionId = resolutionContent ? resolutionContent.id : 'default';\n");
		sb.append(
				"      resolutionContent.querySelectorAll('.tab').forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); t.tabIndex = -1; });\n");
		sb.append("      tab.classList.add('active'); tab.setAttribute('aria-selected', 'true'); tab.tabIndex = 0;\n");
		sb.append("      const target = tab.dataset.tab;\n");
		sb.append("      resolutionContent.querySelectorAll('.tab-content').forEach(content => {\n");
		sb.append("        if(content.id === target) {\n");
		sb.append(
				"          content.classList.add('active'); content.setAttribute('aria-hidden', 'false'); content.tabIndex = 0;\n");
		sb.append("          const restored = restoreChildTabState(resolutionId, target, content);\n");
		sb.append("          if (!restored) {\n");
		sb.append(
				"            const firstSubTab = content.querySelector('.heading-subtab, .elements-tab, .issues-tab');\n");
		sb.append("            if (firstSubTab) { firstSubTab.click(); }\n");
		sb.append("          }\n");
		sb.append(
				"        } else { content.classList.remove('active'); content.setAttribute('aria-hidden', 'true'); content.tabIndex = -1; }\n");
		sb.append("      });\n");
		sb.append("    });\n");
		sb.append("  });\n");
		sb.append("}\n");
		sb.append("// Heading Sub-tabs\n");
		sb.append("function initHeadingSubtabs() {\n");
		sb.append("  document.querySelectorAll('.tab-content').forEach(tabContent => {\n");
		sb.append("    const headingTabs = tabContent.querySelectorAll('.heading-subtab');\n");
		sb.append("    const headingPanels = tabContent.querySelectorAll('.heading-detail');\n");
		sb.append("    headingTabs.forEach(tab => {\n");
		sb.append("      tab.addEventListener('click', () => {\n");
		sb.append(
				"        headingTabs.forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); t.tabIndex = -1; });\n");
		sb.append(
				"        tab.classList.add('active'); tab.setAttribute('aria-selected', 'true'); tab.tabIndex = 0;\n");
		sb.append("        const target = tab.dataset.tab;\n");
		sb.append("        headingPanels.forEach(panel => {\n");
		sb.append(
				"          if(panel.id === target) { panel.classList.add('active'); panel.setAttribute('aria-hidden', 'false'); panel.tabIndex = 0; }\n");
		sb.append(
				"          else { panel.classList.remove('active'); panel.setAttribute('aria-hidden', 'true'); panel.tabIndex = -1; }\n");
		sb.append("        });\n");
		sb.append("        // Save state\n");
		sb.append("        const resolutionContent = tabContent.closest('.resolution-content');\n");
		sb.append("        const resolutionId = resolutionContent ? resolutionContent.id : 'default';\n");
		sb.append(
				"        const mainTab = resolutionContent ? resolutionContent.querySelector('.tab.active') : null;\n");
		sb.append("        if (mainTab && mainTab.dataset.tab) {\n");
		sb.append("          saveTabState(resolutionId, mainTab.dataset.tab, target);\n");
		sb.append("        }\n");
		sb.append("      });\n");
		sb.append("    });\n");
		sb.append("  });\n");
		sb.append("}\n");
		sb.append("// Elements Sub-tabs\n");
		sb.append("function initElementsSubtabs() {\n");
		sb.append("  document.querySelectorAll('.tab-content').forEach(tabContent => {\n");
		sb.append("    const elemTabs = tabContent.querySelectorAll('.elements-tab');\n");
		sb.append("    const elemPanels = tabContent.querySelectorAll('.element-details');\n");
		sb.append("    elemTabs.forEach(tab => {\n");
		sb.append("      tab.addEventListener('click', () => {\n");
		sb.append(
				"        elemTabs.forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); t.tabIndex = -1; });\n");
		sb.append(
				"        tab.classList.add('active'); tab.setAttribute('aria-selected', 'true'); tab.tabIndex = 0;\n");
		sb.append("        const target = tab.dataset.tab;\n");
		sb.append("        elemPanels.forEach(panel => { panel.hidden = panel.id !== target; });\n");
		sb.append("        // Save state\n");
		sb.append("        const resolutionContent = tabContent.closest('.resolution-content');\n");
		sb.append("        const resolutionId = resolutionContent ? resolutionContent.id : 'default';\n");
		sb.append(
				"        const mainTab = resolutionContent ? resolutionContent.querySelector('.tab.active') : null;\n");
		sb.append("        if (mainTab && mainTab.dataset.tab) {\n");
		sb.append("          saveTabState(resolutionId, mainTab.dataset.tab, target);\n");
		sb.append("        }\n");
		sb.append("      });\n");
		sb.append("    });\n");
		sb.append("  });\n");
		sb.append("}\n");
		sb.append("// Issues Sub-tabs\n");
		sb.append("function initIssuesSubtabs() {\n");
		sb.append("  document.querySelectorAll('.tab-content').forEach(tabContent => {\n");
		sb.append("    const issueTabs = tabContent.querySelectorAll('.issues-tab');\n");
		sb.append("    const issuePanels = tabContent.querySelectorAll('.issue-section');\n");
		sb.append("    issueTabs.forEach(tab => {\n");
		sb.append("      tab.addEventListener('click', () => {\n");
		sb.append(
				"        issueTabs.forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); t.tabIndex = -1; });\n");
		sb.append(
				"        tab.classList.add('active'); tab.setAttribute('aria-selected', 'true'); tab.tabIndex = 0;\n");
		sb.append("        const target = tab.dataset.tab;\n");
		sb.append("        issuePanels.forEach(panel => { panel.hidden = panel.id !== target; });\n");
		sb.append("        // Save state\n");
		sb.append("        const resolutionContent = tabContent.closest('.resolution-content');\n");
		sb.append("        const resolutionId = resolutionContent ? resolutionContent.id : 'default';\n");
		sb.append(
				"        const mainTab = resolutionContent ? resolutionContent.querySelector('.tab.active') : null;\n");
		sb.append("        if (mainTab && mainTab.dataset.tab) {\n");
		sb.append("          saveTabState(resolutionId, mainTab.dataset.tab, target);\n");
		sb.append("        }\n");
		sb.append("      });\n");
		sb.append("    });\n");
		sb.append("  });\n");
		sb.append("}\n");
		sb.append("// Screenshot Navigation - called from button click\n");
		sb.append("// Navigates through all screenshots in visual order (top to bottom)\n");
		sb.append("function navigateScreenshotFromButton(button, direction, event) {\n");
		sb.append("  // Prevent default behavior and stop event propagation\n");
		sb.append("  if (event) {\n");
		sb.append("    event.preventDefault();\n");
		sb.append("    event.stopPropagation();\n");
		sb.append("  }\n");
		sb.append("  // Find the screenshot wrapper from the button\n");
		sb.append("  const navContainer = button.closest('.screenshot-navigation');\n");
		sb.append("  if (!navContainer) return;\n");
		sb.append("  const screenshotWrapper = navContainer.closest('.screenshot-wrapper');\n");
		sb.append("  if (!screenshotWrapper) return;\n");
		sb.append("  // Find the screenshots tab (parent container)\n");
		sb.append("  const screenshotsTab = screenshotWrapper.closest('.tab-content[id$=\"-screenshots\"]');\n");
		sb.append("  if (!screenshotsTab) return;\n");
		sb.append("  // Get all screenshots in visual order (all screenshot wrappers in the tab)\n");
		sb.append("  const allScreenshots = Array.from(screenshotsTab.querySelectorAll('.screenshot-wrapper'));\n");
		sb.append("  if (allScreenshots.length === 0) return;\n");
		sb.append("  // Find current index\n");
		sb.append("  let currentIndex = allScreenshots.indexOf(screenshotWrapper);\n");
		sb.append("  if (currentIndex === -1) {\n");
		sb.append("    // Fallback: find by display style\n");
		sb.append("    allScreenshots.forEach((screenshot, i) => {\n");
		sb.append("      const computedStyle = window.getComputedStyle(screenshot);\n");
		sb.append("      if (computedStyle.display !== 'none' && screenshot.style.display !== 'none') {\n");
		sb.append("        currentIndex = i;\n");
		sb.append("      }\n");
		sb.append("    });\n");
		sb.append("    if (currentIndex === -1) currentIndex = 0;\n");
		sb.append("  }\n");
		sb.append("  // Calculate new index\n");
		sb.append("  let newIndex = currentIndex;\n");
		sb.append("  if (direction === 'next') {\n");
		sb.append("    newIndex = currentIndex + 1;\n");
		sb.append("  } else if (direction === 'prev') {\n");
		sb.append("    newIndex = currentIndex - 1;\n");
		sb.append("  }\n");
		sb.append("  // Validate new index\n");
		sb.append("  if (newIndex < 0 || newIndex >= allScreenshots.length) return;\n");
		sb.append("  // Hide all and show selected\n");
		sb.append("  allScreenshots.forEach((screenshot, i) => {\n");
		sb.append("    screenshot.style.display = i === newIndex ? 'flex' : 'none';\n");
		sb.append("  });\n");
		sb.append("  // Update all navigation buttons and counters across all screenshots\n");
		sb.append("  const allNavs = screenshotsTab.querySelectorAll('.screenshot-navigation');\n");
		sb.append("  allNavs.forEach((nav) => {\n");
		sb.append("    const prevBtn = nav.querySelector('button:first-child');\n");
		sb.append("    const nextBtn = nav.querySelector('button:last-child');\n");
		sb.append("    const counter = nav.querySelector('.screenshot-counter');\n");
		sb.append("    if (prevBtn) prevBtn.disabled = (newIndex === 0);\n");
		sb.append("    if (nextBtn) nextBtn.disabled = (newIndex === allScreenshots.length - 1);\n");
		sb.append("    if (counter) counter.textContent = (newIndex + 1) + ' / ' + allScreenshots.length;\n");
		sb.append("  });\n");
		sb.append("}\n");
		sb.append("// Legacy function for backward compatibility\n");
		sb.append("function navigateScreenshot(resolutionId, elementType, direction) {\n");
		sb.append("  // Find resolution content - try multiple methods\n");
		sb.append("  let resolutionContent = document.getElementById(resolutionId);\n");
		sb.append("  if (!resolutionContent) {\n");
		sb.append("    // Try to find by resolution-* pattern\n");
		sb.append("    resolutionContent = document.querySelector('[id^=\"resolution-\"]');\n");
		sb.append("  }\n");
		sb.append("  if (!resolutionContent) {\n");
		sb.append("    // Try to find active resolution content\n");
		sb.append("    resolutionContent = document.querySelector('.resolution-content.active');\n");
		sb.append("  }\n");
		sb.append("  if (!resolutionContent) {\n");
		sb.append("    console.error('Could not find resolution content');\n");
		sb.append("    return;\n");
		sb.append("  }\n");
		sb.append("  // Find screenshots tab - try with resolutionId first, then search for active tab\n");
		sb.append("  let screenshotsTab = resolutionContent.querySelector('#' + resolutionId + '-screenshots');\n");
		sb.append("  if (!screenshotsTab) {\n");
		sb.append("    screenshotsTab = resolutionContent.querySelector('.tab-content[id$=\"-screenshots\"]');\n");
		sb.append("  }\n");
		sb.append("  if (!screenshotsTab) {\n");
		sb.append("    console.error('Could not find screenshots tab');\n");
		sb.append("    return;\n");
		sb.append("  }\n");
		sb.append("  // Find the specific group for this element type\n");
		sb.append("  const allGroups = Array.from(screenshotsTab.querySelectorAll('.screenshot-element-group'));\n");
		sb.append("  let group = allGroups.find(g => {\n");
		sb.append("    const title = g.querySelector('.screenshot-element-group-title');\n");
		sb.append("    if (!title) return false;\n");
		sb.append("    const titleText = title.textContent.toLowerCase();\n");
		sb.append("    return titleText.includes(elementType.toLowerCase());\n");
		sb.append("  });\n");
		sb.append("  // If group not found by type, use first group\n");
		sb.append("  if (!group && allGroups.length > 0) {\n");
		sb.append("    group = allGroups[0];\n");
		sb.append("  }\n");
		sb.append("  if (!group) {\n");
		sb.append("    console.error('Could not find element group');\n");
		sb.append("    return;\n");
		sb.append("  }\n");
		sb.append("  const allScreenshots = Array.from(group.querySelectorAll('.screenshot-viewer-container'));\n");
		sb.append("  if (allScreenshots.length === 0) {\n");
		sb.append("    console.error('No screenshots found');\n");
		sb.append("    return;\n");
		sb.append("  }\n");
		sb.append("  // Find the currently visible screenshot\n");
		sb.append("  let currentIndex = -1;\n");
		sb.append("  allScreenshots.forEach((screenshot, i) => {\n");
		sb.append("    const computedStyle = window.getComputedStyle(screenshot);\n");
		sb.append("    const inlineDisplay = screenshot.style.display;\n");
		sb.append("    const computedDisplay = computedStyle.display;\n");
		sb.append("    if (inlineDisplay !== 'none' && computedDisplay !== 'none') {\n");
		sb.append("      currentIndex = i;\n");
		sb.append("    }\n");
		sb.append("  });\n");
		sb.append("  // If no visible screenshot found, default to first\n");
		sb.append("  if (currentIndex === -1) currentIndex = 0;\n");
		sb.append("  // Calculate new index based on direction\n");
		sb.append("  let newIndex = currentIndex;\n");
		sb.append("  if (direction === 'next') {\n");
		sb.append("    newIndex = currentIndex + 1;\n");
		sb.append("  } else if (direction === 'prev') {\n");
		sb.append("    newIndex = currentIndex - 1;\n");
		sb.append("  } else {\n");
		sb.append("    // If direction is a number, use it as absolute index\n");
		sb.append("    newIndex = parseInt(direction);\n");
		sb.append("    if (isNaN(newIndex)) return;\n");
		sb.append("  }\n");
		sb.append("  // Validate new index\n");
		sb.append("  if (newIndex < 0 || newIndex >= allScreenshots.length) {\n");
		sb.append("    console.log('Invalid index:', newIndex, 'out of', allScreenshots.length);\n");
		sb.append("    return;\n");
		sb.append("  }\n");
		sb.append("  // Hide all screenshots and show the selected one\n");
		sb.append("  allScreenshots.forEach((screenshot, i) => {\n");
		sb.append("    screenshot.style.display = i === newIndex ? 'flex' : 'none';\n");
		sb.append("  });\n");
		sb.append("  // Update all navigation buttons and counters in this group\n");
		sb.append("  const allNavs = group.querySelectorAll('.screenshot-navigation');\n");
		sb.append("  allNavs.forEach((nav) => {\n");
		sb.append("    const prevBtn = nav.querySelector('button:first-child');\n");
		sb.append("    const nextBtn = nav.querySelector('button:last-child');\n");
		sb.append("    const counter = nav.querySelector('.screenshot-counter');\n");
		sb.append("    if (prevBtn) {\n");
		sb.append("      prevBtn.disabled = (newIndex === 0);\n");
		sb.append("    }\n");
		sb.append("    if (nextBtn) {\n");
		sb.append("      nextBtn.disabled = (newIndex === allScreenshots.length - 1);\n");
		sb.append("    }\n");
		sb.append("    if (counter) counter.textContent = (newIndex + 1) + ' / ' + allScreenshots.length;\n");
		sb.append("  });\n");
		sb.append("}\n");
		sb.append("// Initialize on page load\n");
		sb.append("document.addEventListener('DOMContentLoaded', () => {\n");
		sb.append("  initThemeToggle();\n");
		sb.append("  initMainTabs();\n");
		sb.append("  initHeadingSubtabs();\n");
		sb.append("  initElementsSubtabs();\n");
		sb.append("  initIssuesSubtabs();\n");
		sb.append("  document.querySelectorAll('.resolution-content.active').forEach(resolutionContent => {\n");
		sb.append("    let activeTab = resolutionContent.querySelector('.tab.active');\n");
		sb.append("    if(!activeTab) {\n");
		sb.append("      const firstMainTab = resolutionContent.querySelector('.tab');\n");
		sb.append("      if(firstMainTab) {\n");
		sb.append(
				"        resolutionContent.querySelectorAll('.tab').forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); t.tabIndex = -1; });\n");
		sb.append(
				"        firstMainTab.classList.add('active'); firstMainTab.setAttribute('aria-selected', 'true'); firstMainTab.tabIndex = 0; activeTab = firstMainTab;\n");
		sb.append("        const targetTabContent = document.getElementById(firstMainTab.dataset.tab);\n");
		sb.append("        if(targetTabContent) {\n");
		sb.append(
				"          resolutionContent.querySelectorAll('.tab-content').forEach(tc => { tc.classList.remove('active'); tc.setAttribute('aria-hidden', 'true'); tc.tabIndex = -1; });\n");
		sb.append(
				"          targetTabContent.classList.add('active'); targetTabContent.setAttribute('aria-hidden', 'false'); targetTabContent.tabIndex = 0;\n");
		sb.append("        }\n");
		sb.append("      }\n");
		sb.append("    }\n");
		sb.append("    if(activeTab) {\n");
		sb.append("      const targetTabContent = document.getElementById(activeTab.dataset.tab);\n");
		sb.append("      if(targetTabContent) {\n");
		sb.append("        const resolutionId = resolutionContent.id;\n");
		sb.append(
				"        const restored = restoreChildTabState(resolutionId, activeTab.dataset.tab, targetTabContent);\n");
		sb.append("        if (!restored) {\n");
		sb.append("          const firstHeadingTab = targetTabContent.querySelector('.heading-subtab');\n");
		sb.append(
				"          if(firstHeadingTab && !firstHeadingTab.classList.contains('active')) { firstHeadingTab.click(); }\n");
		sb.append("          const firstElemTab = targetTabContent.querySelector('.elements-tab');\n");
		sb.append(
				"          if(firstElemTab && !firstElemTab.classList.contains('active')) { firstElemTab.click(); }\n");
		sb.append("          const firstIssueTab = targetTabContent.querySelector('.issues-tab');\n");
		sb.append(
				"          if(firstIssueTab && !firstIssueTab.classList.contains('active')) { firstIssueTab.click(); }\n");
		sb.append("        }\n");
		sb.append("      }\n");
		sb.append("    }\n");
		sb.append("  });\n");
		sb.append("});\n");
		sb.append("</script>\n");

		sb.append("</div>\n");
		sb.append("</body>\n</html>\n");

		// Generate dynamic filename with URL
		String url;
		if (!websiteDataList.isEmpty()) {
			url = data.url;
		} else if (!resolutionDataMap.isEmpty()) {
			url = resolutionDataMap.values().iterator().next().url;
		} else {
			url = "unknown";
		}

		String sanitizedUrl = sanitizeForFilename(url);
		// Generate date-time string in format (DD-MM-YYYY-HHMMSS)
		String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy-HHmmss"));
		String filename = "QA_Report " + sanitizedUrl + "_" + dateTime + ".html";

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
			writer.write(sb.toString());
		}

		LOGGER.info("Dashboard saved to: " + filename);
	}

	/**
	 * Sanitizes a URL string to be safe for use in a filename. Removes or replaces
	 * invalid filename characters.
	 */
	private String sanitizeForFilename(String url) {
		if (url == null || url.isEmpty()) {
			return "unknown";
		}

		// Remove protocol (http://, https://)
		String sanitized = url.replaceFirst("^https?://", "");

		// Remove www. prefix
		sanitized = sanitized.replaceFirst("^www\\.", "");

		// Replace invalid filename characters with underscores
		sanitized = sanitized.replaceAll("[<>:\"/\\|?*]", "_");

		// Replace dots (except the last one if it's a file extension) with underscores
		// But keep dots that are part of the domain
		sanitized = sanitized.replaceAll("\\.", "_");

		// Replace multiple underscores with single underscore
		sanitized = sanitized.replaceAll("_{2,}", "_");

		// Remove leading/trailing underscores
		sanitized = sanitized.replaceAll("^_+|_+$", "");

		// Limit length to avoid filesystem issues (keep it reasonable, e.g., 200 chars)
		if (sanitized.length() > 200) {
			sanitized = sanitized.substring(0, 200);
		}

		// If empty after sanitization, use a default
		if (sanitized.isEmpty()) {
			sanitized = "unknown";
		}

		return sanitized;
	}

	private static String escapeHtml(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
				"&#39;");
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
		String screenshotBase64;
		List<OtherElement> otherElements;
		List<ElementScreenshotData> elementScreenshots; // Screenshots with highlighted elements

		public WebsiteData(String url, String title, String metaDescription, Map<String, List<HeadingElement>> headers,
				List<ButtonOrLink> buttons, List<ParagraphStyle> paragraphs, List<GrammarIssue> grammarIssues,
				List<BrokenLink> brokenLinks, String screenshotBase64, List<OtherElement> otherElements) {
			this.url = url;
			this.title = title;
			this.metaDescription = metaDescription;
			this.headers = headers;
			this.buttons = buttons;
			this.paragraphs = paragraphs;
			this.grammarIssues = grammarIssues;
			this.brokenLinks = brokenLinks;
			this.screenshotBase64 = screenshotBase64;
			this.otherElements = otherElements;
			this.elementScreenshots = new ArrayList<>();
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

	static class OtherElement {
		String tagName;
		String text;
		String attr1, attr2, attr3, attr4, attr5, attr6, attr7, attr8;

		public OtherElement(String tagName, String text, String attr1, String attr2, String attr3, String attr4,
				String attr5, String attr6, String attr7, String attr8) {
			this.tagName = tagName;
			this.text = text;
			this.attr1 = attr1;
			this.attr2 = attr2;
			this.attr3 = attr3;
			this.attr4 = attr4;
			this.attr5 = attr5;
			this.attr6 = attr6;
			this.attr7 = attr7;
			this.attr8 = attr8;
		}
	}

	static class ScreenSize {
		String name;
		int width;
		int height;
		String description;

		public ScreenSize(String name, int width, int height, String description) {
			this.name = name;
			this.width = width;
			this.height = height;
			this.description = description;
		}
	}

	static class ElementScreenshotData {
		String elementType; // "button", "link", "heading", "paragraph", "other"
		String elementId; // Unique identifier
		String screenshotBase64; // Screenshot with highlighted element
		String highlightColor; // Color used for highlighting
		int x, y, width, height; // Element position and size
		Map<String, String> properties; // Element properties (font-family, font-size, etc.)
		String text; // Element text content
		int index; // Index within its category

		public ElementScreenshotData(String elementType, String elementId, String screenshotBase64,
				String highlightColor, int x, int y, int width, int height, Map<String, String> properties, String text,
				int index) {
			this.elementType = elementType;
			this.elementId = elementId;
			this.screenshotBase64 = screenshotBase64;
			this.highlightColor = highlightColor;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.properties = properties;
			this.text = text;
			this.index = index;
		}
	}

	/**
	 * TestNG BeforeSuite: Initialize logging configuration before any tests run
	 */
	@BeforeSuite(alwaysRun = true)
	public static void setUpSuite() {
		muteLogs();
		LOGGER.info("TestNG Suite Setup: Logging configuration initialized");
	}

	/**
	 * TestNG BeforeClass: Setup WebDriver and initialize test data structures
	 */
	@BeforeClass(alwaysRun = true)
	public void setUpClass() {
		LOGGER.info("TestNG Class Setup: Initializing test class");
		websiteDataList = new ArrayList<>();
		resolutionDataMap = new LinkedHashMap<>();
	}

	/**
	 * TestNG AfterClass: Cleanup WebDriver and resources after all tests in class
	 */
	@AfterClass(alwaysRun = true)
	public void tearDownClass() {
		LOGGER.info("TestNG Class Teardown: Cleaning up resources");
		if (driver != null) {
			try {
				driver.quit();
				LOGGER.info("WebDriver closed successfully");
			} catch (Exception e) {
				LOGGER.warning("Error closing WebDriver: " + e.getMessage());
			}
			driver = null;
			wait = null;
		}
	}

	/**
	 * TestNG Test: Main test method to run QA audit for all screen resolutions This
	 * replaces the original main() method
	 */
	@Test(description = "Run comprehensive QA audit for all screen resolutions", priority = 1, enabled = true)
	public void testQAAuditForAllResolutions() throws Exception {
		LOGGER.info("Starting TestNG test: QA Audit for all resolutions - URL: " + TEST_URL);

		// Execute the QA audit for all enabled resolutions
		runQAAuditForAllResolutions(TEST_URL);

		// TestNG Assertions: Verify that data was collected
		Assert.assertNotNull(websiteDataList, "Website data list should not be null");
		Assert.assertFalse(websiteDataList.isEmpty(), "Website data list should not be empty after audit");
		Assert.assertNotNull(resolutionDataMap, "Resolution data map should not be null");

		LOGGER.info("Test completed successfully. Data collected for " + websiteDataList.size() + " resolution(s)");
	}

	/**
	 * TestNG Test: Single resolution test (optional - for testing specific
	 * resolution)
	 */
	@Test(description = "Run QA audit for default resolution (1920x1080)", priority = 2, enabled = false) // Disabled by
																											// default,
																											// enable if
																											// needed
	public void testQAAuditForDefaultResolution() throws Exception {
		LOGGER.info("Starting TestNG test: QA Audit for default resolution - URL: " + TEST_URL);

		// Execute the QA audit for default resolution
		runQAAudit(TEST_URL);

		// TestNG Assertions: Verify that data was collected
		Assert.assertNotNull(websiteDataList, "Website data list should not be null");
		Assert.assertFalse(websiteDataList.isEmpty(), "Website data list should not be empty after audit");

		LOGGER.info("Default resolution test completed successfully");
	}
}