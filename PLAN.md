Android Offline Document Builder App — Technical Plan

This app is essentially a private field-report/document composer:

Start from reusable HTML templates (“schemas”)

Take photos directly inside the app

Add text blocks attached to photos

Persist everything locally after every action

Export as PDF or DOCX

Share through Android share sheet (WhatsApp, email, etc.)

No cloud, no accounts, no backend, no internet requirement


The best architecture is a native Android app using Kotlin + Jetpack Compose, with an embedded HTML rendering/export pipeline.


---

1. Core Requirements

Functional Requirements

Template System

User can create/import HTML templates

Templates contain placeholders/sections

Templates stored locally

Templates editable inside app


Document Creation

Create new document from template

Add:

photos

captions

free text blocks

timestamps (optional)

metadata (optional)



Camera Integration

Use Android CameraX

Photos stored locally

Auto-compress images

Insert image immediately into HTML document


Autosave

After EVERY action:

Save raw HTML

Save metadata JSON

Save image references

Persist draft state


Draft Management

List of:

drafts

completed exports

templates


Actions:

open

duplicate

export

share

delete


Export

Supported:

PDF

DOCX

raw HTML


Sharing

Use Android Intent Share API:

Gmail

WhatsApp

Telegram

Drive

etc.


Offline Only

No:

accounts

analytics

cloud sync

server

login

remote APIs



---

2. Recommended Tech Stack

Primary Stack

Area	Recommendation

Language	Kotlin
UI	Jetpack Compose
Architecture	MVVM
Persistence	Room + local files
Camera	CameraX
HTML Rendering	Android WebView
PDF Export	Android Print Framework
DOCX Export	Apache POI or docx4j
Image Handling	Coil
Dependency Injection	Hilt
Async	Kotlin Coroutines



---

3. Why Kotlin + Compose

Kotlin is now the standard for Android and generally produces cleaner and safer Android code. Research on Android app quality also shows Kotlin-based apps tend to reduce common Android code smells. 

Advantages:

lightweight

modern Android APIs

strong offline support

easier maintenance

excellent CameraX support

native PDF generation support



---

4. Recommended Architecture

Clean Architecture

UI Layer
 ├── Compose Screens
 ├── ViewModels
 └── State Management

Domain Layer
 ├── Document Engine
 ├── Export Services
 ├── Template Engine
 └── Media Processor

Data Layer
 ├── Room Database
 ├── File Storage
 ├── HTML Persistence
 └── Image Repository


---

5. Data Model

Project Structure On Device

/DocumentsBuilder/

  /templates/
      inspection.html
      report.html

  /drafts/
      /draft_001/
          document.html
          metadata.json
          export.pdf
          export.docx
          /images/
              img_001.jpg
              img_002.jpg

  /exports/


---

6. HTML Document Strategy

Why HTML Is The Right Core Format

HTML gives:

lightweight structure

easy template system

easy image embedding

direct PDF rendering

easy future migration


The app should treat HTML as the “source of truth”.


---

7. Template System Design

Example Template

<html>
<head>
<style>
body {
  font-family: sans-serif;
  padding: 24px;
}

.photo {
  margin-bottom: 20px;
}

img {
  width: 100%;
  border-radius: 8px;
}
</style>
</head>

<body>
<h1>{{title}}</h1>

<div id="content"></div>

</body>
</html>


---

8. Image Insertion Flow

Workflow

Take Photo
   ↓
Compress Image
   ↓
Save to Draft Folder
   ↓
Generate HTML block
   ↓
Insert into DOM
   ↓
Autosave HTML


---

9. HTML Block Structure

Example generated content:

<div class="photo-block">
    <img src="images/img_001.jpg" />
    <p>Broken cable found near entrance.</p>
</div>


---

10. PDF Export

Best Option

Use:

WebView

Android Print Framework


Process:

HTML → WebView → PrintDocumentAdapter → PDF

Advantages:

native

lightweight

accurate rendering

no server



---

11. DOCX Export

Recommended Approach

Use:

Apache POI OR

docx4j


Recommendation:

Apache POI is simpler.

Process:

HTML parsed
   ↓
Converted to DOCX paragraphs/images
   ↓
Exported locally


---

12. Best Open Source Libraries

Camera

CameraX

Official Android camera framework.

Advantages:

stable

modern

lifecycle-aware


Official site: CameraX Android Developers


---

DOCX Export

Apache POI

Java/Kotlin library for Office formats.

Official: Apache POI

Advantages:

mature

offline

reliable DOCX generation



---

Alternative DOCX

docx4j

Official: docx4j

Better HTML conversion support but heavier than POI.


---

Image Loading

Coil

Official: Coil

Lightweight Kotlin image library.


---

HTML Parsing

Jsoup

Official: jsoup

Used for:

modifying HTML

inserting image blocks

sanitizing templates



---

13. Storage Strategy

Use Android SAF (Storage Access Framework)

Advantages:

modern Android compliant

no dangerous storage permissions

privacy-friendly


Apps like BeauTyXT specifically use SAF to avoid broad file access permissions. 


---

14. Suggested UI Screens

Screens

Home

New document

Templates

Drafts

Exports


Template Editor

HTML editor

Preview


Document Editor

live preview

add photo

add text

reorder blocks


Export Screen

PDF

DOCX

HTML


Draft Manager

open

rename

delete

share



---

15. Recommended UI Style

Minimal:

dark/light mode

large buttons

field-use friendly

optimized for one-handed use


Avoid:

animations

cloud features

heavy editors



---

16. Performance Strategy

Important

DO NOT:

embed huge base64 images in HTML


Instead:

save images as files

reference relative paths


This keeps:

memory low

HTML lightweight

exports fast



---

17. Security / Privacy

The app should:

request NO INTERNET permission

operate fully offline

store only locally

use encrypted storage optionally


Optional:

biometric lock



---

18. Export Sharing Flow

Generate PDF/DOCX
   ↓
Save to cache/export folder
   ↓
Launch Android Share Intent


---

19. Open Source Projects Worth Studying

Android Offline Document Apps

ONLYOFFICE Docs

Large office suite with open-source components. 

BeauTyXT

Good reference for:

offline-first

SAF usage

lightweight Android architecture 


Episteme Reader

Modern Jetpack Compose offline document app. 


---

20. Recommended Final Architecture

Best Overall Solution

Stack

Kotlin
Jetpack Compose
Room
CameraX
WebView
Jsoup
Apache POI
Android Print Framework


---

21. Development Phases

Phase 1 — MVP

create template

take photo

add text

autosave HTML

export PDF


Phase 2

DOCX export

draft manager

share menu

reorder sections


Phase 3

template editor

image annotations

signature support


Phase 4

encryption

backup/import

advanced styling



---

22. Estimated Complexity

Feature	Complexity

Camera integration	Medium
HTML templating	Easy
PDF export	Easy
DOCX export	Medium
Offline persistence	Easy
Sharing	Easy
Template editor	Medium



---

23. Recommendation

The cleanest solution is:

Native Android App

NOT Flutter NOT React Native NOT Electron/Web wrapper

Why:

smaller APK

better camera support

easier PDF generation

lower RAM usage

fully offline

better long-term maintainability



---

24. Suggested Project Name

Examples:

FieldDoc

SnapReport

LocalDocs

ReportBuilder

PhotoReport

PocketReport



---

25. Final Technical Recommendation

Best Design Decision

Use:

HTML as source-of-truth

Everything derives from:

HTML

image folder

metadata JSON


Then:

PDF = rendered HTML

DOCX = transformed HTML


This keeps the architecture extremely simple and robust.
