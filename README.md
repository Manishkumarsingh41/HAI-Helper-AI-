
```
# HelperAI

> A universal Android on-screen AI assistant that captures, understands, and eventually acts on information visible on the user's screen.

HelperAI is an Android-based AI assistant designed to work across other applications.

Instead of requiring users to manually copy text, switch applications, take screenshots, or repeatedly type information, HelperAI is designed around a simple idea:

**See → Understand → Assist → Act**

The current implementation focuses on reliable screen OCR. The long-term goal is to evolve this into a context-aware on-screen AI assistant capable of understanding screen content, extracting structured information, identifying UI elements, and assisting with actions such as form filling.

---

# 1. Project Vision

The long-term vision of HelperAI is to create an Android assistant that can understand what the user is currently looking at.

For example, imagine a user is viewing:

- A job application
- A registration form
- A WhatsApp message
- An email
- A web page
- A PDF
- A product page
- A university application
- A travel booking form

Instead of manually reading information and entering it elsewhere, the user should eventually be able to invoke HelperAI and say:

> "Understand this screen."

HelperAI should then:

1. Capture the relevant screen content.
2. Extract visible text.
3. Understand the content.
4. Identify important entities.
5. Convert unstructured text into structured information.
6. Detect relevant UI fields.
7. Match information to those fields.
8. Ask for confirmation when required.
9. Perform the appropriate action.

The final system therefore aims to move beyond simple OCR.

The evolution is:

```text
Screen
  ↓
Capture
  ↓
OCR
  ↓
Text Understanding
  ↓
Information Extraction
  ↓
Structured Data
  ↓
UI Understanding
  ↓
Field Matching
  ↓
User Confirmation
  ↓
Action
````

---

# 2. Current Project Status

The project is currently at the **Stable OCR Foundation** stage.

## Implemented

### Screen OCR

HelperAI can capture the Android screen and perform OCR using ML Kit Text Recognition.

### Full Screen Mode

The user can choose:

```text
HAI Floating Button
        ↓
   Full Screen
        ↓
 Screen Capture
        ↓
      OCR
```

This mode is intended to process the complete visible screen.

---

### Selected Area Mode

The user can select a specific region of the screen.

The selection area supports:

* Moving
* Resizing
* Scanning only the selected region

Flow:

```text
HAI Floating Button
        ↓
 Selected Area
        ↓
 Selection Rectangle
        ↓
 Move / Resize
        ↓
      SCAN
        ↓
 Screenshot
        ↓
 Crop Selected Region
        ↓
 OCR
```

---

### Floating HAI Button

HelperAI provides a floating circular HAI button that can remain visible while the user interacts with other applications.

The button acts as the main entry point for screen scanning.

Conceptually:

```text
Chrome
WhatsApp
ChatGPT
YouTube
Browser
PDF
Forms
  │
  │
  └──── Floating HAI Button
             │
             ├── Full Screen
             │
             └── Selected Area
```

---

### OCR Result Storage

OCR results are passed through a shared response store.

Current flow:

```text
Accessibility Service
        ↓
OCR
        ↓
Formatted Text
        ↓
HelperAIResponseStore
        ↓
MainActivity
        ↓
Compose UI
```

---

### OCR Text Formatting

The raw OCR output is normalized before being stored.

Current formatting goals include:

* Removing unnecessary spaces
* Normalizing whitespace
* Removing spaces before punctuation
* Cleaning brackets
* Preserving bullet/list structures
* Joining obvious wrapped lines
* Removing excessive blank lines
* Keeping the output readable

Example:

Raw OCR:

```text
Natural   Language
Processing  is  a
branch of Artificial
Intelligence.
```

Formatted result:

```text
Natural Language Processing is a branch of
Artificial Intelligence.
```

---

### Copy OCR Text

The captured OCR text can be copied to the Android clipboard.

Flow:

```text
OCR Result
    ↓
COPY
    ↓
Android Clipboard
    ↓
Paste anywhere
```

This allows the user to paste the captured text into:

* ChatGPT
* Chrome
* WhatsApp
* Notes
* Email
* Documents
* Other applications

---

### Scanning Animation

HelperAI includes a scanning animation to provide visual feedback while OCR processing is happening.

The animation is designed to remain minimal and premium.

Current animation color:

```text
#F25CBE
```

The animation should eventually represent the exact active scan region.

For Full Screen:

```text
┌──────────────────────────────┐
│                              │
│        SCANNING              │
│        ─────────             │
│                              │
│                              │
└──────────────────────────────┘
```

For Selected Area:

```text
┌──────────────────────────────┐
│                              │
│    ┌────────────────────┐    │
│    │                    │    │
│    │     SCANNING       │    │
│    │     ─────────      │    │
│    │                    │    │
│    └────────────────────┘    │
│                              │
└──────────────────────────────┘
```

The scanning animation should only operate within the actual scan region.

---

# 3. Product Architecture

The current architecture is intentionally simple.

The main components are:

```text
                    ┌─────────────────────┐
                    │      Android OS     │
                    └──────────┬──────────┘
                               │
                               │
                    ┌──────────▼──────────┐
                    │ AccessibilityService│
                    │                     │
                    │ Screen Events       │
                    │ Scroll Detection    │
                    │ Window Detection    │
                    └──────────┬──────────┘
                               │
                               │
                         Screenshot
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Screenshot Pipeline │
                    │                     │
                    │ Bitmap Creation     │
                    │ Crop                │
                    │ Region Selection    │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │    ML Kit OCR       │
                    │                     │
                    │ Text Recognition    │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Text Formatter      │
                    │                     │
                    │ Cleanup             │
                    │ Normalization       │
                    │ Line reconstruction │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Response Store      │
                    │                     │
                    │ StateFlow           │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   MainActivity      │
                    │                     │
                    │ Jetpack Compose     │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ User                │
                    │                     │
                    │ Read / Copy / Clear │
                    └─────────────────────┘
```

---

# 4. Current Technical Architecture

## MainActivity

`MainActivity` is responsible for the application's primary UI.

Responsibilities:

* Display HelperAI interface
* Display OCR results
* Observe `HelperAIResponseStore`
* Start floating HAI functionality
* Open Accessibility Settings
* Copy OCR text
* Clear OCR text

The UI is built using Jetpack Compose.

---

# 5. HelperAccessibilityService

`HelperAccessibilityService` is the core component responsible for interacting with the Android system.

Responsibilities include:

* Accessibility event monitoring
* Screen change detection
* Scroll detection
* Floating HAI button
* Scan mode management
* Screenshot capture
* Selected-area handling
* OCR triggering
* Scanning animation
* OCR lifecycle management

The service is what allows HelperAI to operate while another application is in the foreground.

---

# 6. HelperAIResponseStore

`HelperAIResponseStore` acts as the bridge between the background accessibility service and the Compose UI.

Architecture:

```text
HelperAccessibilityService
            │
            │ updateResponse()
            ▼
    HelperAIResponseStore
            │
            │ StateFlow
            ▼
        MainActivity
            │
            ▼
       Compose UI
```

The response store currently uses Kotlin `StateFlow`.

This provides:

* Reactive UI updates
* A single source of truth for the latest OCR response
* Separation between OCR processing and UI

---

# 7. Screen Capture Pipeline

The screen capture pipeline is one of the most important parts of the system.

## Full Screen Pipeline

```text
Accessibility Event
        ↓
Schedule OCR
        ↓
Capture Screenshot
        ↓
Android Screenshot API
        ↓
Hardware Buffer
        ↓
ARGB_8888 Bitmap
        ↓
ML Kit InputImage
        ↓
OCR
        ↓
Formatted Text
        ↓
Response Store
```

---

# 8. Selected Area Pipeline

Selected Area mode adds a crop stage.

```text
User selects region
        ↓
Selection Rectangle
        ↓
User presses SCAN
        ↓
Screen Screenshot
        ↓
Full Bitmap
        ↓
Selected Rectangle Coordinates
        ↓
Bitmap Crop
        ↓
Cropped Bitmap
        ↓
ML Kit OCR
        ↓
Formatted Text
        ↓
Response Store
```

Only the selected region is processed.

This reduces unnecessary OCR processing and allows the user to focus on a particular portion of the screen.

---

# 9. OCR Pipeline

The current OCR system uses ML Kit Text Recognition.

Pipeline:

```text
Bitmap
  ↓
InputImage
  ↓
TextRecognizer
  ↓
OCR Result
  ↓
Raw Text
  ↓
Formatter
  ↓
Clean Text
```

The OCR result should not be treated as the final AI understanding layer.

OCR only answers:

> "What characters appear on the screen?"

The future AI layer will answer:

> "What does this information mean?"

This distinction is important for the architecture.

---

# 10. OCR Formatting Pipeline

The formatting layer sits between OCR and the application state.

```text
Raw OCR
   ↓
Normalize line endings
   ↓
Normalize whitespace
   ↓
Remove unnecessary spaces
   ↓
Normalize punctuation
   ↓
Detect bullets
   ↓
Detect wrapped lines
   ↓
Join related lines
   ↓
Remove excessive blank lines
   ↓
Readable Text
```

The formatter is intentionally separate from OCR.

This makes it possible to replace or improve the formatting system without changing the screenshot or OCR pipeline.

---

# 11. Bitmap Lifecycle

Bitmap lifecycle management is important because OCR processing is asynchronous.

The correct lifecycle is:

```text
Screenshot
    ↓
Create Bitmap
    ↓
Pass Bitmap to OCR
    ↓
WAIT
    ↓
OCR Success / Failure
    ↓
OCR Complete
    ↓
Recycle Bitmap
```

The bitmap must not be recycled before ML Kit has finished processing it.

Incorrect:

```text
Bitmap
  ↓
OCR
  ↓
recycle immediately
  ↓
OCR still reading bitmap
  ↓
ERROR
```

Correct:

```text
Bitmap
  ↓
OCR
  ↓
ML Kit finishes
  ↓
recycle
```

This prevents errors such as:

```text
Failed to lock pixels for bitmap
```

---

# 12. Scan Modes

HelperAI currently supports two scan modes.

## Full Screen

```text
ScanMode.FULL_SCREEN
```

The entire visible screen is processed.

Use cases:

* Long text
* Articles
* Chat conversations
* Web pages
* Documents
* Full-screen forms

---

## Selected Area

```text
ScanMode.SELECTED_AREA
```

Only the selected rectangle is processed.

Use cases:

* Specific paragraph
* One form section
* One message
* A particular card
* A single field
* Small UI region

---

# 13. Floating HAI Architecture

The floating button is implemented as an Android overlay.

Concept:

```text
Android System
      │
      ▼
WindowManager
      │
      ▼
Application Overlay
      │
      ▼
Circular HAI Logo
```

The button supports:

* Floating above other applications
* Dragging
* Tapping
* Opening scan options

Current scan menu:

```text
┌──────────────────────┐
│      Scan Screen     │
│                      │
│   Full Screen        │
│   Selected Area      │
│   Stop Scan          │
└──────────────────────┘
```

---

# 14. Accessibility Architecture

HelperAI uses Android Accessibility Service functionality to observe screen-related events.

The service can receive events such as:

```text
TYPE_VIEW_SCROLLED
TYPE_WINDOW_CONTENT_CHANGED
TYPE_WINDOW_STATE_CHANGED
TYPE_VIEW_TEXT_CHANGED
```

These events can trigger OCR scheduling.

The basic logic is:

```text
Accessibility Event
       ↓
Is scanning enabled?
       ↓
Is HelperAI itself?
       ↓
Debounce / Delay
       ↓
Screenshot
       ↓
OCR
```

The event system is not itself the OCR system.

It acts as a trigger mechanism.

---

# 15. OCR Scheduling

The service should not take a screenshot for every accessibility event.

A screen can generate many events during a single interaction.

Therefore the pipeline uses scheduling/debouncing:

```text
Event
Event
Event
Event
Event
 │
 └──── debounce ────► Screenshot
```

This reduces:

* Duplicate OCR
* CPU usage
* Memory usage
* Battery consumption
* Excessive screenshots

---

# 16. Current Data Flow

Complete current data flow:

```text
                   USER
                    │
                    ▼
            Floating HAI Button
                    │
            ┌───────┴────────┐
            │                │
            ▼                ▼
       Full Screen      Selected Area
            │                │
            │          Selection Frame
            │                │
            │          Move / Resize
            │                │
            └───────┬────────┘
                    │
                    ▼
              Screen Capture
                    │
                    ▼
                 Bitmap
                    │
                    ▼
               Crop if needed
                    │
                    ▼
                 ML Kit
                    │
                    ▼
                  OCR
                    │
                    ▼
             Text Formatter
                    │
                    ▼
          HelperAIResponseStore
                    │
                    ▼
               StateFlow
                    │
                    ▼
              MainActivity
                    │
             ┌──────┴──────┐
             │             │
             ▼             ▼
           READ          COPY
```

---

# 17. Current User Experience

The current user experience is:

```text
1. Open HelperAI
        ↓
2. Enable required permissions
        ↓
3. Start HAI
        ↓
4. Floating HAI logo appears
        ↓
5. Open another application
        ↓
6. Tap HAI
        ↓
7. Select:
      ├── Full Screen
      └── Selected Area
        ↓
8. OCR runs
        ↓
9. Text appears in HelperAI
        ↓
10. User can Copy or Clear
```

---

# 18. Permissions

Current system-level permissions/features include:

## Draw Over Other Apps

Required for the floating HAI button.

Android permission:

```xml
android.permission.SYSTEM_ALERT_WINDOW
```

---

## Accessibility Service

Required for:

* Accessibility events
* Screen interaction awareness
* Scroll detection
* Background screen assistance

The service is declared using:

```xml
android.permission.BIND_ACCESSIBILITY_SERVICE
```

---

# 19. Technology Stack

## Android

Primary platform:

```text
Android
```

---

## Kotlin

Primary programming language:

```text
Kotlin
```

---

## Jetpack Compose

Used for the main application UI.

---

## Android AccessibilityService

Used for screen event observation and cross-application assistance.

---

## Android WindowManager

Used for the floating HAI overlay.

---

## Android Screenshot API

Used for capturing the visible screen.

---

## Google ML Kit Text Recognition

Used for OCR.

---

## Kotlin Coroutines

Used for asynchronous processing and reactive state collection.

---

## StateFlow

Used for communicating OCR results to the Compose UI.

---

# 20. Project Structure

The project is organized approximately as follows:

```text
HelperAI/
│
├── app/
│   │
│   ├── src/
│   │   │
│   │   └── main/
│   │       │
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── manish/
│   │       │           └── helperai/
│   │       │               │
│   │       │               ├── MainActivity.kt
│   │       │               │
│   │       │               ├── HelperAccessibilityService.kt
│   │       │               │
│   │       │               └── HelperAIResponseStore.kt
│   │       │
│   │       └── res/
│   │           │
│   │           ├── drawable/
│   │           │
│   │           ├── mipmap/
│   │           │
│   │           ├── values/
│   │           │
│   │           └── xml/
│   │
│   ├── build.gradle.kts
│   │
│   └── ...
│
├── gradle/
│
├── build.gradle.kts
│
├── settings.gradle.kts
│
└── README.md
```

---

# 21. Important Components

| Component                       | Responsibility                                 |
| ------------------------------- | ---------------------------------------------- |
| `MainActivity.kt`               | Main UI                                        |
| `HelperAccessibilityService.kt` | Screen monitoring, overlay, screenshot and OCR |
| `HelperAIResponseStore.kt`      | OCR state communication                        |
| Accessibility configuration     | Service configuration                          |
| Drawable resources              | HAI logo and visual resources                  |
| Compose UI                      | Application interface                          |

---

# 22. Long-Term Architecture

The current OCR architecture is only the first layer.

The planned production architecture is:

```text
                    ┌───────────────────┐
                    │   User Screen     │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Screen Capture    │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ OCR / Vision      │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Text Reconstruction│
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Content           │
                    │ Understanding     │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Entity Extraction │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Structured Data   │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ UI Understanding  │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Field Matching    │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Action Planner    │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ User Confirmation │
                    └─────────┬─────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Android Action    │
                    └───────────────────┘
```

---

# 23. Future AI Pipeline

The future AI pipeline is divided into multiple stages.

## Stage 1: Capture

```text
Screen
 ↓
Screenshot
```

---

## Stage 2: Vision

```text
Screenshot
 ↓
OCR
 ↓
Text + Coordinates
```

The system should eventually preserve:

* Text
* Bounding boxes
* Confidence
* Position
* Reading order
* UI region

---

## Stage 3: Text Reconstruction

OCR output can contain:

* Broken words
* Incorrect spacing
* Incorrect line breaks
* Wrong reading order

A reconstruction layer should convert this into coherent text.

---

## Stage 4: Semantic Understanding

An LLM or specialized NLP layer should understand what the screen contains.

Example:

```text
Name: Manish Kumar Singh
Email: manish@example.com
Phone: 9876543210
Date of Birth: 12/05/2003
```

The AI understands:

```json
{
  "name": "Manish Kumar Singh",
  "email": "manish@example.com",
  "phone": "9876543210",
  "date_of_birth": "12/05/2003"
}
```

---

# 24. Structured Information Extraction

The next major intelligence layer will convert unstructured screen content into structured data.

Example:

Input:

```text
Please contact Manish Kumar Singh at
manish@example.com or 9876543210.
```

Output:

```json
{
  "person": {
    "name": "Manish Kumar Singh",
    "email": "manish@example.com",
    "phone": "9876543210"
  }
}
```

This becomes the bridge between OCR and automation.

---

# 25. Form Understanding

The next stage is understanding forms.

Example screen:

```text
Name
[________________]

Email
[________________]

Phone
[________________]

Date of Birth
[________________]
```

The vision/UI system should identify:

```text
Field 1 → Name
Field 2 → Email
Field 3 → Phone
Field 4 → Date of Birth
```

---

# 26. Field Matching

Once structured data exists:

```text
Extracted Data
       ↓
Field Detection
       ↓
Semantic Matching
```

Example:

```text
"email address"
       ↕
"user email"
       ↕
"email"
```

The AI should understand that these refer to the same concept.

---

# 27. Automatic Form Filling

Long-term goal:

```text
Source Information
        ↓
OCR
        ↓
Structured Data
        ↓
Form Detection
        ↓
Field Matching
        ↓
Auto Fill
```

Example:

```text
Source:

Name:
Manish Kumar Singh

Email:
manish@example.com

Phone:
9876543210
```

Target form:

```text
Full Name
[                    ]

Email
[                    ]

Mobile
[                    ]
```

HelperAI should eventually map:

```text
Name  → Full Name
Email → Email
Phone → Mobile
```

---

# 28. User Confirmation Layer

Automatic actions should not blindly execute.

The future system should provide confirmation for sensitive or irreversible actions.

Example:

```text
HelperAI found:

Name
Manish Kumar Singh

Email
manish@example.com

Phone
9876543210

Fill these fields?

[ Cancel ]    [ Confirm ]
```

This improves:

* Safety
* User control
* Trust
* Reliability

---

# 29. Context Awareness

The final assistant should understand context.

Example:

If the screen contains:

```text
Upload Resume
```

HelperAI should understand that the user is dealing with a job application.

If the screen contains:

```text
Card Number
Expiry
CVV
```

the system should recognize that this is sensitive financial information and apply stricter safety rules.

The system should not treat every OCR result as ordinary text.

---

# 30. Planned Intelligence Layers

The long-term AI stack can be represented as:

```text
Layer 1
Screen Capture

Layer 2
OCR / Vision

Layer 3
Text Reconstruction

Layer 4
NLP

Layer 5
Entity Extraction

Layer 6
Structured Data

Layer 7
UI Understanding

Layer 8
Semantic Field Matching

Layer 9
Action Planning

Layer 10
User Confirmation

Layer 11
Android UI Action
```

---

# 31. Potential Future AI Components

Future versions may use:

* LLMs
* Vision-Language Models
* NLP
* Embeddings
* Structured output
* Function calling
* Tool calling
* RAG where appropriate
* Local models where privacy requires it
* Cloud AI where higher reasoning capability is required

The exact model/provider should remain replaceable.

The architecture should avoid tightly coupling HelperAI to a single AI provider.

---

# 32. Privacy and Security

Because HelperAI operates on screen content, privacy is a major architectural concern.

The future product should follow:

```text
Minimum Data
      ↓
Minimum Processing
      ↓
Minimum Storage
```

Sensitive information should not be unnecessarily stored.

Potential sensitive information includes:

* Passwords
* OTPs
* Payment information
* Personal identity information
* Private conversations
* Emails
* Phone numbers
* Addresses
* Financial information

Future versions should introduce:

* Sensitive-content detection
* Local processing where possible
* Explicit user confirmation
* No unnecessary persistence
* Secure temporary memory
* Secure API communication
* Secret management
* Logging controls

---

# 33. Performance Goals

The production version should optimize for:

### Low latency

OCR should feel nearly instant for small regions.

### Low memory usage

Screenshots and bitmaps should be released correctly.

### Low battery usage

Accessibility events should not trigger unnecessary OCR.

### Efficient OCR

Selected-area OCR should process only the required region.

### Duplicate detection

Identical OCR results should not repeatedly update the application.

---

# 34. Reliability Goals

The system should eventually handle:

* Rotated screens
* Different resolutions
* Different aspect ratios
* Dark mode
* Light mode
* Different fonts
* Different languages
* Dynamic content
* Scrolling
* Animations
* Lazy-loaded content
* WebViews
* Native Android views
* Accessibility limitations
* Screenshot failures
* OCR failures

---

# 35. Multilingual OCR

Future versions should support multiple languages.

Potential pipeline:

```text
Screen
 ↓
Language Detection
 ↓
OCR
 ↓
Language-specific reconstruction
 ↓
Translation / Understanding
```

The AI layer should understand multilingual content rather than assuming English.

---

# 36. Conversation / Context Memory

A future version may maintain temporary task context.

Example:

```text
User:
Fill this form using my resume.

HelperAI:
Resume information extracted.

User:
Continue.

HelperAI:
Next screen detected.
```

The assistant should maintain context for the current task while avoiding unnecessary permanent storage.

---

# 37. Future Product Modes

Possible future modes:

## Scan

Capture and read screen content.

## Understand

Explain what is currently visible.

## Extract

Extract structured information.

## Fill

Fill relevant fields.

## Assist

Suggest the next useful action.

## Act

Perform approved actions.

---

# 38. Example Future User Experience

```text
User opens a job application.

        ↓

Taps HAI.

        ↓

HelperAI scans the screen.

        ↓

Detects:

Name
Email
Phone
Address
Experience

        ↓

HelperAI understands the form.

        ↓

Matches available information.

        ↓

Shows:

"5 fields can be filled."

        ↓

User taps:

CONFIRM

        ↓

HelperAI fills the fields.

        ↓

User remains in control.
```

---

# 39. Development Roadmap

## Phase 1 - OCR Foundation

Status: **Completed**

* [x] Android project
* [x] Accessibility Service
* [x] Floating HAI button
* [x] Full Screen OCR
* [x] Selected Area OCR
* [x] Selection rectangle
* [x] Rectangle movement
* [x] Rectangle resizing
* [x] OCR bitmap lifecycle fix
* [x] OCR text formatting
* [x] Copy OCR text
* [x] Scanning animation
* [x] Soft pink scanning color

---

# Phase 2 - Better OCR

Status: **Planned**

* [ ] Better word reconstruction
* [ ] Better sentence reconstruction
* [ ] Better paragraph detection
* [ ] Reading-order detection
* [ ] OCR confidence handling
* [ ] Multi-language support
* [ ] Better handling of tables
* [ ] Better handling of forms
* [ ] Better handling of chat layouts

---

# Phase 3 - AI Understanding

Status: **Planned**

* [ ] LLM integration
* [ ] Structured output
* [ ] Entity extraction
* [ ] Intent detection
* [ ] Screen summarization
* [ ] Question answering
* [ ] Context understanding
* [ ] Semantic classification

---

# Phase 4 - UI Understanding

Status: **Planned**

* [ ] UI element detection
* [ ] Text field detection
* [ ] Button detection
* [ ] Checkbox detection
* [ ] Dropdown detection
* [ ] Form structure detection
* [ ] Field-label relationship detection
* [ ] Coordinate-aware understanding

---

# Phase 5 - Intelligent Form Filling

Status: **Planned**

* [ ] Extract personal information
* [ ] Detect target fields
* [ ] Semantic field matching
* [ ] Confidence scoring
* [ ] User confirmation
* [ ] Automatic field filling
* [ ] Error recovery

---

# Phase 6 - Action Agent

Status: **Future**

* [ ] Action planning
* [ ] Safe UI interaction
* [ ] Click actions
* [ ] Scroll actions
* [ ] Navigation assistance
* [ ] Form submission assistance
* [ ] Confirmation before sensitive actions
* [ ] Task completion detection

---

# Phase 7 - Production AI Assistant

Status: **Long-Term**

```text
Universal Screen Understanding
            +
       AI Reasoning
            +
      UI Understanding
            +
      Safe Automation
```

Goal:

> Build a reliable AI assistant that can understand the user's current screen and help complete tasks across Android applications while keeping the user in control.

---

# 40. Engineering Principles

HelperAI should follow these principles:

## 1. Build incrementally

Every phase should produce a working system.

## 2. Do not break stable functionality

Existing OCR functionality should remain stable while new intelligence layers are added.

## 3. Separate responsibilities

OCR, formatting, AI reasoning, UI detection and automation should remain separate modules.

## 4. Keep providers replaceable

AI models should be replaceable without rewriting the whole application.

## 5. User remains in control

Automation should not silently perform sensitive actions.

## 6. Privacy by design

Screen data should be handled carefully.

## 7. Performance matters

Screen assistance should feel lightweight and responsive.

---

# 41. Development Checkpoints

The project uses stable checkpoints so development can continue safely.

## Checkpoint 1 - Stable OCR

Current checkpoint:

```text
Full Screen OCR          ✅
Selected Area OCR        ✅
Floating HAI             ✅
Selection Rectangle      ✅
Move / Resize            ✅
OCR Formatting           ✅
Copy Text                ✅
Scanning Animation       ✅
```

This checkpoint is stored in the GitHub repository.

Future development should continue from this checkpoint instead of rebuilding the application from scratch.

---

# 42. Git Workflow

Before major changes:

```bash
git status
```

Create a checkpoint:

```bash
git add .
git commit -m "Stable OCR checkpoint"
git push
```

For feature development:

```bash
git add .
git commit -m "Add <feature>"
git push
```

Recommended commit style:

```text
Add OCR text copy
Fix bitmap lifecycle
Add selected area scanning
Update scanning animation
Improve OCR formatting
Add AI extraction pipeline
Add form field detection
```

---

# 43. Local Development

Open the project in Android Studio.

Build:

```text
Build → Make Project
```

Run:

```text
Run → Run 'app'
```

Install on an Android device.

Required system permissions must be enabled manually.

---

# 44. Testing Strategy

Testing should eventually cover:

## OCR

* Small text
* Large text
* Multiple paragraphs
* Chat messages
* Forms
* Tables
* Different fonts

## Selection

* Small selection
* Large selection
* Screen edges
* Moving selection
* Resizing selection

## Scrolling

* Short page
* Long page
* Fast scrolling
* Slow scrolling
* Dynamic content

## Accessibility

* Chrome
* Messaging applications
* Chat applications
* Web pages
* Forms
* Documents

## Reliability

* Screenshot failure
* OCR failure
* Permission removal
* App restart
* Service restart
* Device rotation

---

# 45. Known Limitations

The current version is primarily an OCR system.

It does not yet provide complete:

* Semantic screen understanding
* Structured information extraction
* UI element understanding
* Automatic form filling
* General-purpose AI reasoning
* Autonomous task execution

OCR quality is also dependent on:

* Screen resolution
* Font size
* Image quality
* Contrast
* Language
* UI layout
* OCR model limitations

---

# 46. Current vs Final Product

## Current

```text
Screen
 ↓
OCR
 ↓
Readable Text
 ↓
Copy
```

## Final Goal

```text
Screen
 ↓
Vision
 ↓
OCR
 ↓
Text Reconstruction
 ↓
AI Understanding
 ↓
Structured Information
 ↓
UI Understanding
 ↓
Field Matching
 ↓
Action Planning
 ↓
User Confirmation
 ↓
Safe Automation
```

This distinction defines the overall development strategy.

---

# 47. Why HelperAI Is Different

Traditional OCR tools generally stop at:

```text
Image → Text
```

HelperAI aims to continue:

```text
Image
 ↓
Text
 ↓
Meaning
 ↓
Structure
 ↓
Context
 ↓
Action
```

The goal is therefore not simply to build another OCR application.

The goal is to build an **AI layer over the Android screen**.

---

# 48. Final Product Vision

The final HelperAI experience should feel simple to the user.

The complexity should remain behind the scenes.

The user should only need to:

```text
Tap HAI
   ↓
Tell / choose what they want
   ↓
HelperAI understands the screen
   ↓
HelperAI suggests what it can do
   ↓
User confirms
   ↓
HelperAI performs the task
```

The system should progressively evolve from:

**OCR tool**

to

**Screen understanding system**

to

**AI assistant**

to

**safe task automation agent.**

---

# 49. Future Architecture Summary

The complete long-term system can be summarized as:

```text
                         HELPERAI
                            │
                            ▼
                  ┌──────────────────┐
                  │ Screen Capture   │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ Vision + OCR     │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ Text Processing  │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ NLP / LLM        │
                  │ Understanding    │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ Structured Data  │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ UI Understanding │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ Semantic Matching│
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ Action Planner   │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ User Confirmation│
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ Safe UI Actions  │
                  └──────────────────┘
```

---

# 50. Project Status

**Current Stage:** Stable OCR Foundation

**Platform:** Android

**Language:** Kotlin

**UI:** Jetpack Compose

**OCR:** ML Kit Text Recognition

**Screen Access:** Accessibility Service + Android Screenshot APIs

**Overlay:** Android WindowManager

**State Management:** Kotlin StateFlow

**Current Focus:** Reliable screen OCR and clean text extraction

**Next Major Focus:** AI-powered screen understanding and structured information extraction

**Final Goal:** A privacy-conscious, context-aware Android AI assistant capable of understanding on-screen content and assisting with real-world tasks through safe, user-controlled automation.

---

# License

License to be decided.

````

### Architecture ka main idea

Is README mein sabse important distinction ye hai:

**Abhi:**

```text
Screen → OCR → Clean Text → Copy
````

**Final product:**

```text
Screen
  ↓
Vision/OCR
  ↓
Text Reconstruction
  ↓
NLP/LLM
  ↓
Structured Data
  ↓
UI Understanding
  ↓
Field Matching
  ↓
Action Planning
  ↓
User Confirmation
  ↓
Safe Automation
```

Yahi tumhare project ka **actual long-term architecture** hona chahiye. Current OCR code ko directly AI/form-filling code ke saath mix nahi karna. Har layer separate rakhenge, taaki project bada hone par maintain karna easy rahe.

**GitHub mein `README.md` update karke commit/push:**

```bash
git add README.md
git commit -m "Add detailed project architecture and roadmap"
git push
```

Ab repository mein future ke liye proper documentation bhi saved rahegi.
