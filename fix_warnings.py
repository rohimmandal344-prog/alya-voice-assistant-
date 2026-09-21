import os
import re

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Divider -> HorizontalDivider
    content = content.replace("Divider(", "HorizontalDivider(")
    
    # Icons.Filled to Icons.AutoMirrored.Filled
    icons_to_replace = ["OpenInNew", "VolumeUp", "HelpOutline", "Backspace", "VolumeDown", "Chat", "ArrowBack", "Send"]
    for icon in icons_to_replace:
        content = content.replace(f"Icons.Filled.{icon}", f"Icons.AutoMirrored.Filled.{icon}")
        content = content.replace(f"Icons.Default.{icon}", f"Icons.AutoMirrored.Filled.{icon}")
        
    # Modifier.menuAnchor() -> Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
    content = content.replace("Modifier.menuAnchor()", "Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true)")
    
    # String.capitalize()
    content = content.replace(".capitalize()", ".replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }")
    
    # Locale constructor
    content = re.sub(r'java\.util\.Locale\("([^"]+)"\)', r'java.util.Locale.forLanguageTag("\1")', content)
    content = re.sub(r'java\.util\.Locale\("([^"]+)", "([^"]+)"\)', r'java.util.Locale.Builder().setLanguage("\1").setRegion("\2").build()', content)
    
    # OptIn Coroutines
    if "kotlinx.coroutines.ExperimentalCoroutinesApi" not in content and "kotlinx.coroutines.flow" in content:
        content = content.replace("package com.example", "package com.example\n\nimport kotlinx.coroutines.ExperimentalCoroutinesApi\n")
        # Just use OptIn where needed if possible, or suppress.
        # Actually it's easier to just suppress it.
        
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed {filepath}")

for root, _, files in os.walk('app/src/main/java'):
    for file in files:
        if file.endswith('.kt'):
            fix_file(os.path.join(root, file))
