# ETRX Code Reviewer Plugin

A JetBrains plugin for AI-powered code review using Ollama models.

## Features

- **AI Code Review**: Automatically review code changes using AI models
- **Ollama Integration**: Support for Ollama models with dynamic model selection
- **Customizable Prompts**: Multiple review templates (security, performance, general)
- **VCS Integration**: Review selected changes or all changes from commit panel
- **Popup Results**: Clean, formatted review results displayed in popup dialogs
- **Configuration UI**: Easy setup and customization through IDE settings

## Installation

1. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```

2. Install the plugin from the generated ZIP file in `build/distributions/`

## Configuration

1. Go to **File > Settings > Tools > Code Reviewer**
2. Configure your Ollama endpoint (default: http://192.168.66.181:11434)
3. Select AI model from the dropdown (with refresh button to load latest models)
4. Select or create custom prompt templates
5. Use "Default" buttons next to each field for quick reset to default values
6. Use "Reset to Defaults" button to reset all settings at once
7. Test the connection to ensure everything works

## Usage

### Review VCS Changes
- **Ctrl+Alt+R**: Review selected changes
- **Ctrl+Alt+Shift+R**: Review all changes
- Click the AI review button (💡) in VCS commit panel
- Right-click in VCS commit panel and select "Review with AI"

### Review Code in Editor
1. Select code in the editor
2. Right-click and select "Review Code with AI"
3. View results in a popup dialog with formatted markdown content

## Default Configuration

- **AI Model**: qwen3:8b
- **Endpoint**: http://192.168.66.181:11434
- **API Path**: /api/generate
- **Temperature**: 0.7
- **Max Tokens**: 2048

## Supported File Types

The plugin automatically filters and reviews code files including:
- Java, Kotlin, Scala
- Python, JavaScript, TypeScript
- C/C++, C#, Go, Rust
- SQL, XML, HTML, CSS
- Configuration files (JSON, YAML, Properties)

## Requirements

- JetBrains IDE (IntelliJ IDEA, Android Studio, etc.)
- Java 21 or higher
- Ollama server running with qwen3:8b model (or configured alternative)

## Development

### Building
```bash
./gradlew build
```

### Running in Development
```bash
./gradlew runIde
```

### Testing
```bash
./gradlew test
```

## License

This project is licensed under the MIT License.