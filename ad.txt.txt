# HAI - Helper AI

HAI (Helper AI) is an Android-based screen assistance application that can capture text from other apps using on-device OCR.

The goal of HAI is to make it easier to understand, collect, and work with information displayed on the screen without manually copying text.

## Current Features

- Floating circular HAI button
- Works over other applications
- Full Screen OCR
- Selected Area OCR
- Resizable selection rectangle
- Movable selection rectangle
- OCR while scrolling
- Clean and readable OCR text formatting
- OCR results displayed inside the HelperAI app
- Clear OCR response option
- Google ML Kit Text Recognition

## Current OCR Flow

```text
Open any app
      ↓
Tap HAI floating button
      ↓
Choose scan mode
      ↓
 ┌─────────────────┐
 │ Full Screen     │
 │       OR        │
 │ Selected Area   │
 └─────────────────┘
      ↓
Capture screen
      ↓
OCR with Google ML Kit
      ↓
Clean and format text
      ↓
Store OCR response
      ↓
Display in HelperAI