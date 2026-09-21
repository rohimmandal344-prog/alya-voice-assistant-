package com.example.domain.tools

import android.Manifest

object ToolRegistry {

    val tools: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "manage_call",
            description = "Manages incoming/active calls (answer_by_assistant, reject_call, assistant_speak, answer_call, end_call).",
            parameters = mapOf("action" to "Call action: 'answer_by_assistant', 'reject_call', 'assistant_speak', 'answer_call', 'end_call'"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "control_device",
            description = "Controls Android device UI and navigation (scroll, open_app, press_home, press_back, lock_screen, take_screenshot).",
            parameters = mapOf("action" to "Device action: 'scroll', 'open_app', 'press_home', 'press_back', 'lock_screen'", "direction" to "down/up", "app_name" to "App name"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "control_media",
            description = "Controls media playback (play_video, pause, resume, stop, play_music, play_shorts, adjust_volume).",
            parameters = mapOf("action" to "Media action: 'play_video', 'pause', 'resume', 'stop', 'play_music'", "query" to "Media search query"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "browse_web",
            description = "Handles web search, downloading files, weather retrieval, and website navigation.",
            parameters = mapOf("action" to "Web action: 'download_file', 'get_weather', 'search_web', 'open_website'", "query" to "Search query or URL", "location" to "City/locality"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "accessibility_control",
            description = "Performs hands-free actions like scrolling, skipping YouTube reels, clicking buttons, or sending WhatsApp messages. Needs user to grant accessibility permission once.",
            parameters = mapOf("action" to "Action to perform: 'scroll_down' (skip reel), 'scroll_up' (previous reel), 'like_post', 'click_text', 'send_whatsapp'", "text" to "Optional exact text on screen to click"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_app",
            description = "Opens an installed Android application like YouTube, Camera, Maps, Browser, Calculator, Gallery, Dialer, Messages, or Clock.",
            parameters = mapOf("appName" to "Name or alias of application (e.g. youtube, camera, maps, calculator, browser)"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "search_and_play_media",
            description = "Searches for a specific video, song, artist, or content and plays it on YouTube, Spotify, or default media player.",
            parameters = mapOf(
                "query" to "Search query or video/song title to play",
                "app" to "Target media app (e.g. 'youtube', 'spotify', or 'media')"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_settings",
            description = "Opens system settings for Display, Sound, Notifications, Location, Battery, Date & Time, or App Info.",
            parameters = mapOf("target" to "Settings section: display, sound, notifications, location, battery, datetime, or general"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "toggle_wifi",
            description = "Turns device Wi-Fi on or off directly or opens the instant Wi-Fi control panel.",
            parameters = mapOf("state" to "'on' or 'off'"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "check_wifi_status",
            description = "Checks and reports the exact current Wi-Fi status, whether it is enabled, disabled, or actively connected to an SSID network.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "connect_wifi",
            description = "Attempts to connect to an authorized or previously saved Wi-Fi network directly, or guides connection to a specific SSID.",
            parameters = mapOf("network" to "Optional SSID/network name"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "disconnect_wifi",
            description = "Disconnects from the current active Wi-Fi network.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "toggle_bluetooth",
            description = "Turns device Bluetooth on or off.",
            parameters = mapOf("state" to "'on' or 'off'"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "toggle_mobile_data",
            description = "Opens internet and mobile data connectivity settings or panel.",
            parameters = mapOf("state" to "'on' or 'off'"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "toggle_flashlight",
            description = "Turns the device flashlight/torch on or off.",
            parameters = mapOf("state" to "'on', 'off', or 'toggle'"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "create_timer",
            description = "Starts a timer on the device clock with given duration in seconds.",
            parameters = mapOf(
                "seconds" to "Duration in seconds (e.g. 600 for 10 minutes)",
                "message" to "Label for the timer"
            ),
            riskLevel = ToolRiskLevel.MEDIUM,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "create_alarm",
            description = "Sets an alarm for a specific hour and minute.",
            parameters = mapOf(
                "hour" to "Hour (0-23)",
                "minute" to "Minute (0-59)",
                "message" to "Alarm label"
            ),
            riskLevel = ToolRiskLevel.MEDIUM,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "create_wakeup_alarm",
            description = "Schedules Alya's personal voice wake-up alarm that personally wakes the user with Alya's voice greeting in the morning.",
            parameters = mapOf(
                "hour" to "Hour (0-23)",
                "minute" to "Minute (0-59)",
                "title" to "Wake up label"
            ),
            riskLevel = ToolRiskLevel.MEDIUM,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "make_call",
            description = "Prepares or initiates a phone call to a given phone number or contact.",
            parameters = mapOf(
                "recipient" to "Phone number or contact name to call"
            ),
            riskLevel = ToolRiskLevel.HIGH,
            requiredPermission = Manifest.permission.CALL_PHONE,
            defaultRequiresConfirmation = true
        ),
        ToolDefinition(
            name = "prepare_message",
            description = "Prepares an SMS text message to a contact or phone number.",
            parameters = mapOf(
                "recipient" to "Phone number or contact name",
                "body" to "Message body text"
            ),
            riskLevel = ToolRiskLevel.HIGH,
            defaultRequiresConfirmation = true
        ),
        ToolDefinition(
            name = "open_maps",
            description = "Starts navigation or searches a location in Google Maps.",
            parameters = mapOf(
                "query" to "Destination address, place name, or coordinates"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "check_weather",
            description = "Checks and reports the local weather conditions, temperature, humidity, wind, and forecast for the user's location or a specified city.",
            parameters = mapOf("location" to "City name or 'local' for current location"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_camera",
            description = "Launches the camera application.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "share_content",
            description = "Opens the Android Sharesheet to share text content with other apps.",
            parameters = mapOf("text" to "Text content to share"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "copy_clipboard",
            description = "Copies the provided text to the Android clipboard.",
            parameters = mapOf("text" to "Text string to copy"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "search_contacts",
            description = "Searches device address book for contact names and retrieves their phone numbers.",
            parameters = mapOf("query" to "Name or number of the contact to search for"),
            riskLevel = ToolRiskLevel.LOW,
            requiredPermission = Manifest.permission.READ_CONTACTS,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "answer_call",
            description = "Answers an incoming ringing phone call via voice command.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.MEDIUM,
            requiredPermission = Manifest.permission.ANSWER_PHONE_CALLS,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "end_call",
            description = "Ends or declines the current active or ringing phone call via voice command.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.MEDIUM,
            requiredPermission = Manifest.permission.ANSWER_PHONE_CALLS,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "control_volume",
            description = "Adjusts device volume up, down, mute, or to a specific level (0-100%).",
            parameters = mapOf(
                "action" to "Volume operation: 'up', 'down', 'mute', 'unmute', or 'set'",
                "level" to "Optional volume level percentage (0 to 100)"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "control_brightness",
            description = "Adjusts screen brightness up, down, or to a specific percentage (0-100%).",
            parameters = mapOf(
                "action" to "Brightness operation: 'up', 'down', or 'set'",
                "level" to "Optional screen brightness percentage (0 to 100)"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "control_media",
            description = "Controls media playback on the device (play, pause, next track, previous track).",
            parameters = mapOf(
                "action" to "Media action: 'play', 'pause', 'toggle', 'next', 'previous', or 'stop'"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "send_whatsapp",
            description = "Prepares or sends a WhatsApp message to a specific contact or phone number.",
            parameters = mapOf(
                "recipient" to "Contact name or phone number",
                "message" to "Text body of the WhatsApp message"
            ),
            riskLevel = ToolRiskLevel.HIGH,
            defaultRequiresConfirmation = true
        ),
        ToolDefinition(
            name = "schedule_task",
            description = "Schedules a reminder or calendar task at a specific time or delay.",
            parameters = mapOf(
                "title" to "Title or summary of the reminder or task",
                "time_minutes" to "Minutes in the future from now to trigger the reminder",
                "repeat" to "Optional repeat interval: 'NONE', 'DAILY', or 'WEEKLY'"
            ),
            riskLevel = ToolRiskLevel.MEDIUM,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_website",
            description = "Navigates to a specific website URL in the web browser.",
            parameters = mapOf(
                "url" to "The web address (e.g. 'https://wikipedia.org' or 'google.com')"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "search_web",
            description = "Performs a web search using the user's default browser.",
            parameters = mapOf(
                "query" to "The search query string"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "go_home",
            description = "Minimizes the assistant and returns to the Android home screen.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "game_assistant",
            description = "Provides assistance for gaming, dice rolling, coin flips, random numbers, or chess/trivia.",
            parameters = mapOf(
                "action" to "Game aid: 'roll_dice', 'flip_coin', 'random_number', 'chess_tip', or 'trivia'",
                "detail" to "Optional parameters like max number or specific dice sides"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "device_link",
            description = "Checks status, battery, storage, RAM, or applications across linked phones, tablets, and computers, or initiates pairing.",
            parameters = mapOf(
                "action" to "Action to perform: 'status', 'list', 'link_phone', 'link_computer', 'refresh', or 'battery'",
                "device_name" to "Optional target device name (e.g. 'macbook', 'phone', 'computer')"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "get_device_info",
            description = "Gathers and displays comprehensive real-time device specs, telemetry, battery level, charging status, free and total RAM, storage capacity, network type, IP address, running apps, OS version, hardware model, and security fingerprint.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "app_share",
            description = "Shares the Alya app and latest update package with another phone, tablet, or computer via Quick Share, Bluetooth, Device Link, or download link.",
            parameters = mapOf(
                "action" to "Action: 'share_apk', 'beam', 'push_to_device', 'share_link', 'show_qr', or 'status'",
                "target_device" to "Optional target device name (e.g. 'phone', 'laptop', 'macbook')"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "restart_phone",
            description = "Triggers the phone restart and power options dialog, allowing the user to restart or power off their phone.",
            parameters = mapOf(
                "action" to "Power action: 'restart' or 'power_off'"
            ),
            riskLevel = ToolRiskLevel.HIGH,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "scan_installed_apps",
            description = "Scans and audits all applications installed on the user's phone, giving exact total app counts, pre-installed system apps count, user-installed third-party apps count, and security risk assessment.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "get_exact_app_count",
            description = "Queries the exact count of total apps, default system apps, and user-installed apps on the phone without vague estimates.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_application",
            description = "Opens an installed Android application like WhatsApp, YouTube, Camera, Maps, Settings, or Chrome.",
            parameters = mapOf("app_name" to "Name or alias of application to open"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "close_application",
            description = "Closes the current or specified foreground application and returns to the home screen.",
            parameters = mapOf("app_name" to "Optional name of application to close"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "scroll_screen",
            description = "Performs directional screen scrolling gestures via Accessibility Service.",
            parameters = mapOf(
                "direction" to "Direction to scroll: 'UP', 'DOWN', 'LEFT', or 'RIGHT'",
                "distance" to "Optional scroll distance: 'SHORT' or 'LONG'"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "touch_element",
            description = "Touches or clicks a visible UI element by coordinates or text/content label via Accessibility.",
            parameters = mapOf(
                "element_identifier" to "Optional visible text or content description of the element to click",
                "x" to "Optional X coordinate on screen (integer)",
                "y" to "Optional Y coordinate on screen (integer)"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "press_home_button",
            description = "Simulates pressing the Android system Home button.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "press_back_button",
            description = "Simulates pressing the Android system Back button.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "make_phone_call",
            description = "Places a phone call to a given contact name or phone number.",
            parameters = mapOf(
                "contact_name" to "Name of contact to call",
                "phone_number" to "Phone number to dial"
            ),
            riskLevel = ToolRiskLevel.HIGH,
            defaultRequiresConfirmation = true
        ),
        ToolDefinition(
            name = "type_text",
            description = "Types text into the active focused input field or target field.",
            parameters = mapOf(
                "target_field" to "Optional name or label of target field",
                "text_content" to "Text content to type"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "media_control",
            description = "Controls media playback on the device.",
            parameters = mapOf(
                "action" to "Media action: 'PLAY', 'PAUSE', 'NEXT', 'PREVIOUS', or 'MUTE'"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "adjust_volume",
            description = "Adjusts device volume up or down by a specific level change.",
            parameters = mapOf(
                "level_change" to "Volume change integer (e.g. 10 for +10%, -10 for -10%)"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "send_whatsapp_message",
            description = "Sends a WhatsApp message directly to a contact or phone number.",
            parameters = mapOf(
                "recipient" to "Contact name or phone number",
                "message_body" to "Message text to send"
            ),
            riskLevel = ToolRiskLevel.HIGH,
            defaultRequiresConfirmation = true
        ),
        ToolDefinition(
            name = "toggle_setting",
            description = "Toggles a device setting on or off (e.g., 'wifi', 'bluetooth', 'flashlight', 'mobile_data').",
            parameters = mapOf(
                "setting_name" to "Setting name: 'wifi', 'bluetooth', 'flashlight', or 'mobile_data'",
                "state" to "'on' or 'off'"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "shutdown_assistant",
            description = "Terminates active listening and enters standby mode immediately.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "create_document",
            description = "Creates and saves a text document, note, or document draft on the device with title and content.",
            parameters = mapOf(
                "title" to "Document title or subject",
                "content" to "Document body text content"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "set_routine",
            description = "Creates and schedules a recurring daily routine or time-based routine on the phone.",
            parameters = mapOf(
                "name" to "Routine name (e.g., 'Morning Routine', 'Bedtime Routine', 'Workout Routine')",
                "time" to "Time string (e.g., '07:00 AM', '10:00 PM', or delay in minutes)",
                "actions" to "Optional description of routine actions"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "take_screenshot",
            description = "Takes a screenshot of the current screen hands-free via Accessibility.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "lock_screen",
            description = "Locks the device screen immediately hands-free via Accessibility.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_recents",
            description = "Opens recent apps overview screen via Accessibility.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_notifications",
            description = "Pulls down the system notification shade via Accessibility.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_quick_settings",
            description = "Opens the Android quick settings control center via Accessibility.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_power_menu",
            description = "Opens the device power / restart dialog via Accessibility.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "split_screen",
            description = "Toggles multi-window split screen mode via Accessibility.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "turn_on_and_connect_wifi",
            description = "Turns on Wi-Fi and connects to available or specified Wi-Fi network.",
            parameters = mapOf(
                "network" to "Optional SSID name",
                "state" to "'on'"
            ),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "open_voice_settings",
            description = "Lists available voice settings options including trigger names, voice recording, and preferences.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "voice_training",
            description = "Initiates user voice training / voice enrollment across multiple environments.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "recording_progress",
            description = "Provides summary of voice recordings per trigger word and per environment.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "delete_all_recordings",
            description = "Requests deletion of all user voice recordings with required confirmation dialog.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.MEDIUM,
            defaultRequiresConfirmation = true
        ),
        ToolDefinition(
            name = "confirm_delete_recordings",
            description = "Confirms and executes deletion of all user voice recordings.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "change_trigger_name",
            description = "Changes the assistant wake trigger word to one of the approved list.",
            parameters = mapOf("trigger_name" to "Approved trigger name: Alya, Alia, Seno, Jarvis, Luna, Nova, Echo, Iris, Friday, AlyaPro"),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        ),
        ToolDefinition(
            name = "keep_listening",
            description = "Keeps voice conversation listening active without sleeping after a single turn.",
            parameters = emptyMap(),
            riskLevel = ToolRiskLevel.LOW,
            defaultRequiresConfirmation = false
        )
    )

    fun findTool(name: String): ToolDefinition? = tools.find { it.name.equals(name, ignoreCase = true) }
}
