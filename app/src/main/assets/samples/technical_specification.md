# Universal Reader Technical Specification

> **Status:** Approved  
> **Target Release:** v1.0.0  
> **Security Model:** Strict Local Sandbox

---

## 1. Overview and Objectives

Universal Reader provides a unified, uncompromising native Android interface for reading diverse document formats. The architecture emphasizes **deterministic performance**, **zero cloud dependencies**, and **Emil Kowalski design engineering** principles.

### Key Capabilities
- **Direct PDF Rendering**: Hardware-accelerated native PDF page rendering with memory-safe LRU bitmap caching.
- **OpenXML Engine**: Pure Kotlin parsing of Microsoft Office formats (`.docx`, `.xlsx`, `.pptx`) without external server calls.
- **Tabular Data Processing**: RFC 4180 CSV parsing with virtualized grid scrolling.
- **Typographic Fidelity**: Strict 4dp/8dp spatial grid, light-first palette (`#FAF9F6`), and contrast-verified dark mode.

---

## 2. Format Support Matrix

| Format | Extension | Primary Engine | Streaming Support |
| :--- | :--- | :--- | :--- |
| **PDF** | `.pdf` | `android.graphics.pdf.PdfRenderer` | Yes (Page-by-page) |
| **Word** | `.docx` | OpenXML Zip + XmlPullParser | Yes (Document DOM) |
| **Excel** | `.xlsx` | OpenXML Zip + SharedStrings | Yes (Sheet virtualizer) |
| **PowerPoint**| `.pptx` | OpenXML Slide Parser | Yes (Slide Deck) |
| **CSV / TSV** | `.csv`, `.tsv`| RFC 4180 State Machine | Yes (Stream row reader)|
| **Markdown** | `.md` | Tokenizer & Styled AnnotatedString | Yes (Block rendered) |
| **JSON / XML**| `.json`, `.xml`| Platform Parsers + Indenter | Yes (Formatted view) |
| **EPUB** | `.epub` | Open Container Format (OCF) | Yes (Chapter pagination) |

---

## 3. Implementation Code Sample

```kotlin
class DocumentViewModel(private val repository: DocumentRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun loadDocument(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = repository.openDocument(uri)
            _uiState.value = ReaderUiState.Success(content)
        }
    }
}
```

---

## 4. Design Guidelines

1. **Hierarchy over Decoration**: Clear typography defines the screen structure.
2. **Restrained Spacing**: Standard 4dp / 8dp / 16dp increments.
3. **No Decorative AI Slop**: No arbitrary purple gradients, floating glowing balls, or fake metrics.
