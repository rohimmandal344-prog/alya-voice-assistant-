import os
import re

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    content = content.replace("HorizontalHorizontalDivider", "HorizontalDivider")
    content = content.replace("HorizontalDivider", "Divider")
    
    icons_to_replace = ["OpenInNew", "VolumeUp", "HelpOutline", "Backspace", "VolumeDown", "Chat", "ArrowBack", "Send"]
    for icon in icons_to_replace:
        content = content.replace(f"Icons.AutoMirrored.Filled.{icon}", f"Icons.Filled.{icon}")
        content = content.replace(f"Icons.Default.{icon}", f"Icons.Filled.{icon}")
        
    content = content.replace("Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true)", "Modifier.menuAnchor()")
    
    content = content.replace(".replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }", ".capitalize()")
    
    # Locales can't be reverted easily with regex so I will just use git if possible, but this wasn't a git repo...
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed {filepath}")

for root, _, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            fix_file(os.path.join(root, file))
