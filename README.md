# FocusGuard: App Blocker

FocusGuard is a powerful digital wellbeing app designed to help users regain control over their digital habits. The app allows users to block distracting applications, control specific social media features, track screen time, and build healthier digital habits.

## Features

### App Blocking
- Block any installed app on a custom schedule
- Set recurring blocking schedules
- Override blocking for emergency access

### Social Media Control
Block specific features within popular social media apps:
- **YouTube**: Shorts, Search, Picture-in-Picture
- **Instagram**: Stories, Reels, Explore
- **Facebook**: Stories, Reels
- **Snapchat**: Spotlight
- **X (Twitter)**: Explore tab
- **TikTok**: Search, Comments

### Usage Statistics
- Track total screen time 
- Monitor app usage by day, week, and month
- Identify most used apps

### Habit Tracking
- Add and track habits you want to build or break
- Get insights on your progress
- Receive customizable reminders

### Notification Management
- Motivational quotes and content
- Personalized insights
- Custom scheduling for notifications

## Technical Details

### Required Permissions
- Accessibility Service: For monitoring and blocking apps
- Usage Stats: To track app usage
- Overlay Permission: To display blocking screens
- Notification Permission: For reminders and alerts
- Device Admin: For uninstall protection (optional)

### Implementation
- Written in Kotlin
- Follows Material Design principles
- Uses Android Architecture Components

## Google Play Store Publishing Information

### Store Listing

**App Name**: FocusGuard: App Blocker

**Short Description**:
Block apps, limit social media, and build healthier digital habits.

**Full Description**:
FocusGuard helps you regain control over your digital life by blocking distracting apps and features that waste your time and drain your productivity.

✓ BLOCK APPS: Set custom schedules to block distracting apps when you need to focus
✓ LIMIT SOCIAL MEDIA: Block specific features like Instagram Reels, YouTube Shorts, and TikTok feeds
✓ TRACK SCREEN TIME: Monitor your app usage with detailed statistics
✓ BUILD HABITS: Create and track positive digital habits
✓ GET MOTIVATED: Receive customizable notifications with motivational content

Perfect for:
• Students who need to focus on studying
• Professionals looking to increase productivity
• Parents helping children develop healthy screen habits
• Anyone trying to reduce social media addiction

Take back control of your time and attention with FocusGuard.

**Privacy Policy**:
Your privacy is important to us. FocusGuard only collects data necessary for app functionality. Usage data remains on your device and is never transmitted to our servers or third parties.

### Content Rating
- Contains no objectionable content
- Appropriate for all ages

### Target Audience
- Ages 13+
- Focus on productivity and digital wellbeing

## Getting Started

1. Enable the Accessibility Service when prompted
2. Grant Usage Stats permission
3. Optionally enable Device Admin for uninstall protection
4. Start blocking apps and specific features

## Support

For questions, feedback, or support please contact us at support@focusguard.app 

## Developer Information

### API Key Configuration
For security reasons, the OpenAI API key is not stored in the repository. To build and run the app locally:

1. Create or edit the `local.properties` file in the project root
2. Add your OpenAI API key as follows:
   ```
   openai.api.key=your_actual_api_key_here
   ```
3. The app will automatically use this key for AI-powered features
4. Never commit your `local.properties` file to version control

This approach keeps sensitive API keys out of your repository and prevents security issues with GitHub's push protection. 