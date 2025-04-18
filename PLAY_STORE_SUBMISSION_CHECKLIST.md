# Google Play Store Submission Checklist

## Required Items

### Google Play Developer Account
- [ ] Create a Google Play Developer Account (one-time $25 fee)
- [ ] Complete account details and payment information
- [ ] Verify your identity if requested

### App Bundle/APK
- [ ] Generate a signed release APK or Android App Bundle
  - Use `Build > Generate Signed Bundle/APK` in Android Studio
  - Create a new keystore or use an existing one
  - IMPORTANT: Store your keystore file and password securely
- [ ] Test the release build thoroughly on multiple devices
- [ ] Verify app size is within acceptable limits
- [ ] Ensure proper version code and version name in build.gradle

### Store Listing Content
- [ ] App name: "FocusGuard: App Blocker"
- [ ] Short description (80 characters max)
- [ ] Full description (4000 characters max)
- [ ] App category selection (Productivity)
- [ ] Content rating questionnaire completion
- [ ] Email address for contact

### Visual Assets
- [ ] App icon (512px x 512px PNG)
- [ ] Feature graphic (1024px x 500px JPG or PNG)
- [ ] At least 2 screenshots for each supported device type (phone, tablet)
  - Phone screenshots: 16:9 aspect ratio (e.g., 1920x1080)
  - Tablet screenshots: 16:10 aspect ratio
- [ ] Optional: Promotional video (YouTube URL)

### Legal Requirements
- [ ] Privacy policy link/text
  - Upload PRIVACY_POLICY.md to a web host or convert to HTML
  - Provide URL in the Play Console
- [ ] Declare all permissions and explain their usage
- [ ] Complete content rating questionnaire
- [ ] Address data safety form questions
- [ ] Comply with families policy if applicable

## Recommended Items

### App Optimization
- [ ] Implement proper error handling
- [ ] Add crash reporting (e.g., Firebase Crashlytics)
- [ ] Test performance on low-end devices
- [ ] Optimize app size
- [ ] Test on various Android versions and screen sizes

### Store Listing Optimization
- [ ] Research and implement keywords in app title and description
- [ ] Design professional graphics and screenshots
- [ ] Consider localization for major markets (translate store listing)
- [ ] Set up store listing experiments to test different assets

### Pre-launch Steps
- [ ] Join Google Play App Signing
- [ ] Configure in-app updates if applicable
- [ ] Set up staged rollouts (percentage-based rollout)
- [ ] Create a pre-registration campaign if desired

### Post-launch Considerations
- [ ] Monitor for crashes and negative reviews
- [ ] Plan regular updates
- [ ] Consider implementing in-app review API

## Final Review
- [ ] Check app against Google Play [Developer Program Policies](https://play.google.com/about/developer-content-policy/)
- [ ] Ensure app meets quality guidelines
- [ ] Test all features in the release version
- [ ] Verify app works without internet connection if applicable

## Notes
- Initial review by Google Play may take several days (typically 2-7 days)
- Have a response plan ready for potential rejections
- Keep your developer account secure with 2-factor authentication 