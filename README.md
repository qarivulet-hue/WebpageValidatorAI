# WebpageValidatorAI - Project Overview

## 📋 Project Description

**WebpageValidatorAI** is an automated Quality Assurance (QA) tool designed to comprehensively analyze web pages for content quality, styling consistency, SEO optimization, accessibility compliance, and functional integrity. The tool performs multi-resolution testing across various screen sizes and generates an interactive HTML dashboard with detailed findings and recommendations.

## 🎯 Core Functionality

### 1. Multi-Resolution Testing
The tool tests websites across **8 different screen resolutions** to ensure responsive design compliance:

- **Desktop Resolutions:**
  - `1920 × 1080` - Most popular desktop resolution
  - `1440 × 900` - Common medium-size screens
  - `1366 × 768` - Very common on budget laptops

- **Tablet Resolutions:**
  - `1024 × 768` - Standard iPad landscape
  - `768 × 1024` - Standard iPad portrait

- **Mobile Resolutions:**
  - `414 × 896` - Large iPhones
  - `360 × 800` - Most Android devices
  - `340 × 720` - Low-end or small Android screens

Each resolution generates a separate analysis report, allowing developers to identify responsive design issues across different devices.

### 2. SEO Analysis
Comprehensive SEO validation including:

- **Page Title Validation:**
  - Checks for presence and proper length (≤60 characters recommended)
  - Visual indicators for valid/invalid titles

- **Meta Description Validation:**
  - Length checking (warns if exceeds 160 characters)
  - Presence validation

- **SEO Score Calculation (0-100):**
  - **Heading Structure Score** - Evaluates proper heading hierarchy (H1-H6)
  - **Content Quality Score** - Assesses content completeness and quality
  - **Link Health Score** - Measures broken links and link quality

- **Responsive SEO Display:**
  - SEO Information section is automatically excluded for smaller resolutions (tablets and mobile) to optimize dashboard space

### 3. Heading Structure Analysis
Detailed analysis of all heading elements (H1-H6):

- **Extraction & Validation:**
  - Counts headings at each level
  - Validates heading hierarchy
  - Identifies missing or improperly structured headings

- **Styling Analysis:**
  - Font family
  - Font size
  - Font weight (with human-readable names: Thin, Light, Normal, Bold, etc.)
  - Line height
  - Letter spacing
  - Color (with visual color swatches)

- **Visual Presentation:**
  - Color-coded boxes showing heading counts
  - Expandable sections for detailed heading information
  - Tabbed interface for easy navigation between heading levels

### 4. Element Analysis

#### **Buttons:**
- Text content extraction
- ARIA label validation
- Complete styling analysis:
  - Background color (with visual swatches)
  - Text color
  - Font properties (family, size, weight)
  - Padding and border radius
  - Line height and letter spacing
- URL association (if applicable)
- Image button detection with alt text extraction

#### **Links:**
- Text content and href extraction
- ARIA label validation
- Styling properties (same as buttons)
- Image link detection with alt text fallback
- URL validation and display

#### **Paragraphs:**
- Text content extraction
- Typography analysis:
  - Font family
  - Font size
  - Font weight
  - Line height
  - Letter spacing
  - Text color

### 5. Grammar & Spelling Check
Advanced grammar and spelling validation using **LanguageTool**:

- **Comprehensive Text Extraction:**
  - Paragraphs
  - Headings (H1-H6)
  - Buttons and links
  - Meta descriptions
  - Page titles
  - Other text elements (span, div, label, li, td, th)

- **Parallel Processing:**
  - Uses 4 concurrent threads for faster grammar checking
  - Efficient processing of large amounts of text

- **Detailed Issue Reporting:**
  - **Error Text** - The problematic text with context
  - **Suggested Correction** - Recommended fix
  - **Issue Description** - Explanation of the grammar/spelling issue
  - **Full Text Context** - Complete context for better understanding

- **Language Support:**
  - Currently configured for American English
  - Uses LanguageTool library (version 6.3)

### 6. Broken Link Detection
Automated link validation system:

- **HTTP Status Code Checking:**
  - Identifies 404 (Not Found) errors
  - Validates link accessibility

- **Parallel Processing:**
  - Uses 10 concurrent threads for efficient link checking
  - Handles large numbers of links quickly

- **Smart URL Resolution:**
  - Converts relative URLs to absolute URLs
  - Handles various URL formats
  - Skips non-HTTP links (javascript:, mailto:, tel:)

- **Detailed Reporting:**
  - Link text
  - Full URL
  - HTTP status code
  - Source page URL

### 7. Interactive HTML Dashboard
Beautiful, responsive HTML dashboard with:

- **Modern UI Design:**
  - Glass-morphism effects
  - Smooth transitions and animations
  - Color-coded sections for easy navigation
  - Responsive layout for all screen sizes

- **Tabbed Interface:**
  - **SEO Information Tab** - URL, title, meta description, SEO score
  - **Headings Tab** - Complete heading structure analysis
  - **Elements Tab** - Buttons, links, and paragraphs breakdown
  - **Issues Tab** - Spelling/grammar issues and broken links

- **Visual Elements:**
  - Color swatches for text and background colors
  - Expandable/collapsible sections (using HTML `<details>`)
  - Issue cards with color-coded borders
  - Progress indicators and statistics

- **Accessibility Features:**
  - ARIA labels and roles
  - Keyboard navigation support
  - Screen reader friendly
  - Semantic HTML structure

## 🛠️ Technical Stack

### **Technologies:**
- **Java 17** - Core programming language
- **Selenium WebDriver** - Web automation and element extraction
- **LanguageTool** - Grammar and spelling checking
- **Maven** - Build management and dependency resolution
- **TestNG** - Testing framework

### **Key Dependencies:**

| Dependency | Version | Purpose |
|------------|---------|---------|
| `selenium-java` | 4.31.0 | Web automation and browser control |
| `webdrivermanager` | 5.9.3 | Automatic WebDriver management |
| `languagetool-core` | 6.3 | Grammar checking engine |
| `language-en` | 6.3 | English language rules |
| `testng` | 7.11.0 | Testing framework |
| `jsoup` | 1.16.1 | HTML parsing |
| `okhttp` | 4.11.0 | HTTP client for link checking |

### **Browser Configuration:**
- **Chrome/Chromium** - Headless mode for automated testing
- **Window Sizing** - Dynamic window resizing for multi-resolution testing
- **Chrome Options:**
  - `--headless=new` - Runs without GUI
  - `--disable-gpu` - Optimizes performance
  - `--disable-dev-shm-usage` - Prevents shared memory issues

## ✨ Key Features

1. **⚡ Parallel Processing**
   - Grammar checks run in 4 parallel threads
   - Link validation uses 10 parallel threads
   - Significantly reduces processing time

2. **📱 Multi-Resolution Testing**
   - Tests 8 different screen sizes automatically
   - Generates separate reports for each resolution
   - Identifies responsive design issues

3. **♿ Accessibility Validation**
   - ARIA label checking
   - Alt text validation for images
   - Semantic HTML structure analysis

4. **🎨 Visual Styling Analysis**
   - Complete typography analysis
   - Color extraction with visual swatches
   - Spacing and layout measurements

5. **📊 SEO Scoring**
   - Automated SEO score calculation (0-100)
   - Detailed breakdown by category
   - Actionable recommendations

6. **📝 Interactive Dashboard**
   - Modern, user-friendly interface
   - Detailed breakdowns of all findings
   - Easy navigation and filtering

7. **🛡️ Error Handling**
   - Comprehensive error logging
   - Graceful failure handling
   - Continues processing even if individual checks fail

## 📤 Output

The tool generates a comprehensive HTML report file with the naming convention:
```
StyleGuide_ValidatorQA_[domain]_[timestamp].html
```

Example: `StyleGuide_ValidatorQA_amiwebprod_wpenginepowered_com_qa-demo_20251204_184701.html`

### **Report Contents:**
- **Summary Statistics:**
  - Total issues count
  - Spelling issues count
  - Broken links count
  - Element counts (buttons, links, paragraphs)

- **Detailed Element Analysis:**
  - Complete styling information
  - Visual color representations
  - Expandable details for each element

- **Issue Reports:**
  - Grammar/spelling errors with suggestions
  - Broken links with status codes
  - Contextual information for each issue

- **SEO Information:**
  - SEO score and breakdown
  - Page title and meta description validation
  - Recommendations for improvement

- **Visual Styling Information:**
  - Color swatches
  - Typography details
  - Spacing measurements

## 🎯 Use Cases

1. **Website QA Audits**
   - Pre-deployment validation
   - Post-deployment verification
   - Regular quality checks

2. **Style Guide Compliance**
   - Ensures consistent styling across pages
   - Validates typography standards
   - Checks color usage

3. **SEO Optimization**
   - Identifies SEO issues
   - Provides optimization recommendations
   - Tracks SEO score improvements

4. **Accessibility Validation**
   - ARIA label compliance
   - Alt text presence
   - Semantic HTML structure

5. **Content Quality Checks**
   - Grammar and spelling validation
   - Content completeness
   - Text consistency

6. **Cross-Device Compatibility Testing**
   - Responsive design validation
   - Multi-resolution testing
   - Device-specific issue identification

7. **Pre-Deployment Validation**
   - Comprehensive quality checks
   - Issue identification before going live
   - Automated testing pipeline integration

## 🚀 Getting Started

### **Prerequisites:**
- Java 17 or higher
- Maven 3.6+
- Chrome/Chromium browser installed
- Internet connection (for downloading dependencies and testing links)

### **Configuration:**
The main entry point is in `StyleGuide_ValidatorQA.java`:

```java
public static void main(String[] args) throws Exception {
    muteLogs();
    StyleGuide_ValidatorQA qa = new StyleGuide_ValidatorQA();
    qa.runQAAuditForAllResolutions("https://your-website-url.com/");
}
```

### **Running the Tool:**
1. Update the URL in the `main` method
2. Run the Java application
3. Wait for the analysis to complete
4. Open the generated HTML report file

### **Available Methods:**
- `runQAAuditForAllResolutions(String url)` - Tests all 8 screen resolutions
- `runQAAudit(String url)` - Tests single resolution (default: 1920×1080)
- `runQAAudit(String url, ScreenSize screenSize)` - Tests specific resolution

## 📁 Project Structure

```
WebpageValidatorAI/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── WebpageValidatorAIPKG/
│   │           └── App.java
│   └── test/
│       └── java/
│           ├── Cursor_in_Details/
│           │   └── StyleGuide_ValidatorQA.java  (Main QA tool)
│           ├── Cursor_Sample/
│           │   └── Webpage_Validator_QA.java
│           ├── Full_HTML/
│           │   ├── BrokenLinkChecker.java
│           │   ├── BrokenLinksFinder.java
│           │   ├── CountPTags.java
│           │   └── DynamicDashboard.java
│           └── WebpageValidatorAIPKG/
│               ├── CNVT_testNG/
│               │   └── WebQATest.java
│               ├── ConTestNG.java
│               ├── DiffHTML.java
│               ├── SeleniumOpenAIGrammarChecker.java
│               ├── WebsiteQA2HTML.java
│               ├── WebsiteQA2HTMLWeam.java
│               └── WebsiteQAAutomationChat.java
├── pom.xml
└── README.md
```

## 🔧 Configuration Options

### **Screen Sizes:**
Customize screen sizes by modifying the `SCREEN_SIZES` array in `StyleGuide_ValidatorQA.java`:

```java
private static final List<ScreenSize> SCREEN_SIZES = Arrays.asList(
    new ScreenSize("1920 × 1080", 1920, 1080, "Most popular desktop resolution"),
    // Add more screen sizes as needed
);
```

### **Resolutions Without SEO:**
Control which resolutions exclude SEO information:

```java
private static final Set<String> RESOLUTIONS_WITHOUT_SEO = Set.of(
    "1440 × 900",
    "1366 × 768",
    // Add more resolutions as needed
);
```

### **Thread Pool Sizes:**
Adjust parallel processing threads:

```java
private static final int LINK_CHECK_THREADS = 10;  // For link checking
// Grammar checking uses 4 threads (hardcoded in parallelGrammarCheck method)
```

## 📊 Performance Considerations

- **Parallel Processing:** Significantly reduces processing time for large websites
- **Headless Browser:** Faster execution without GUI rendering
- **Efficient Element Extraction:** Uses optimized Selenium selectors
- **Caching:** Reuses WebDriver instance across resolutions

## 🐛 Known Limitations

1. **Language Support:** Currently only supports American English for grammar checking
2. **Browser Support:** Only supports Chrome/Chromium
3. **JavaScript Rendering:** Requires JavaScript-enabled pages to fully load
4. **Rate Limiting:** Link checking may be rate-limited by target websites
5. **Dynamic Content:** May not capture content loaded via AJAX after initial page load

## 🔮 Future Enhancements

- Support for multiple languages in grammar checking
- Additional browser support (Firefox, Safari, Edge)
- Screenshot capture for each resolution
- Performance metrics (page load time, etc.)
- Export to PDF functionality
- Integration with CI/CD pipelines
- API endpoint for programmatic access
- Database storage for historical tracking

## 📝 License

This project is part of an automation practice repository.

## 👥 Contributing

This appears to be a practice/learning project. Contributions and improvements are welcome!

---

**Last Updated:** December 2024  
**Version:** 0.0.1-SNAPSHOT



















