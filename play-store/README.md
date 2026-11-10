# Publishing WMT on Google Play

Everything Play Console asks for, in the order it asks. The answers below come from what the
app's code actually does (as of version 1.1.0, code 3). If a release adds an SDK, a permission
or a new kind of upload, revisit **Data safety** before publishing it.

| File | What it is |
|---|---|
| `listing.md` | Store listing text, category, tags, graphics list |
| `privacy-policy.html` | The privacy policy, a self-contained page to host on your website |
| `graphics/` | Icon and feature graphic (SVG sources, plus exports ready to upload) |

## 0. Before you start

- [x] **Developer name and contact** are filled in: MrUnknown0001, dvlpr.me@gmail.com.
- [ ] **Host the privacy policy** on your website and check that the URL opens in a private browser window. Play rejects a link that redirects to a login page or returns a PDF download.
- [ ] **Back up `wmt-release.jks`** and its passwords somewhere safe outside this PC, such as a password manager or company vault. With Play App Signing this key becomes your *upload key*. If you lose it, you can ask Google to reset it, but that takes days and blocks every update until it's done.
- [ ] **Create a reviewer account** on the production WMT server (see section 3). Google's reviewers cannot get past the sign-in screen without one, and an app they can't open is rejected.

## 1. Developer account

- **Organisation account** (needs a D-U-N-S number): you can publish straight to production after review.
- **Personal account created after 13 Nov 2023**: Google first requires a **closed test with at least 12 testers, opted in for 14 days in a row**, before you can apply for production access. Start that test as soon as possible.

## 2. Create the app

Play Console → **Create app**:

| Field | Answer |
|---|---|
| App name | WMT: Workload Management Tool |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | Free (this cannot be changed later from free to paid) |
| Declarations | Tick both: Developer Program Policies, US export laws |

The package name `com.wmt.app` is set by the first upload and **can never be changed**.

## 3. App content (Policy → App content)

### Privacy policy
The URL from step 0.

### App access → "All or some functionality is restricted"
Add one set of instructions:

```
Name: Reviewer sign-in
On first launch the app asks for a server address. Enter:
    [[PRODUCTION SERVER URL, e.g. https://wmt.example.com]]
Then sign in with:
    Email:    [[REVIEWER EMAIL]]
    Password: [[REVIEWER PASSWORD]]
No other steps are needed. The account has sample projects, tasks and an approval request.
```

Make the reviewer account a normal employee with some sample data, and **never change its
password without updating this form**. Reviewers revisit the app on every update.

### Ads
**No**, the app does not contain ads.

### Content rating (IARC questionnaire)
- Category: **All other app types** (a utility / productivity app, not a game)
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- "Does the app allow users to interact or exchange content?" **Yes.** Users comment on tasks and share files with colleagues.
- "Does the app share the user's current physical location with other users?" **Yes.** Photos taken with the in-app camera can carry their location and are visible to colleagues on the task.
- "Does the app allow users to purchase digital goods?" **No**
- Expected result: rated for everyone or 3+, with the notes "Users Interact" and "Shares Location"

### Target audience and content
- Target age groups: **18 and over** only
- "Could your app unintentionally appeal to children?" **No**

### News app
**No**

### COVID-19 contact tracing and status apps
**My app is not a publicly available COVID-19 contact tracing or status app**

### Data safety
Answer as follows.

**Overview**
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes.** Release builds connect over HTTPS only. |
| Which account creation methods does your app support? | **My app does not allow users to create an account.** Accounts are issued by the organisation that runs the WMT server; the app only signs in to them. |
| Do you provide a way for users to request that their data is deleted? | **Yes.** The privacy policy gives the contact email. |

**Data types.** For every row: *Collected = Yes, Shared = No, Processed ephemerally = No*.
Firebase and your WMT server act on your behalf, which Google counts as a *service provider*, so
nothing here is "shared".

| Data type | Required or optional | Purposes |
|---|---|---|
| Personal info → Name | Required | App functionality, Account management |
| Personal info → Email address | Required | App functionality, Account management |
| Personal info → User IDs | Required | App functionality, Account management |
| Location → Precise location | **Optional** (only if the user allows it, only on in-app photos) | App functionality |
| Photos and videos → Photos | Optional | App functionality |
| Photos and videos → Videos | Optional | App functionality |
| Files and docs → Files and docs | Optional | App functionality |
| App activity → Other user-generated content (comments, tasks, to-dos, notes, time logs, approvals) | Required | App functionality |
| Device or other IDs → Device or other IDs (Firebase push token) | Required | App functionality |

Do **not** tick: Financial info, Health and fitness, Messages (comments live on tasks, not
in a private messaging feature), Audio, Calendar, Contacts, Web browsing, App info and
performance (no crash or diagnostics SDK), Approximate location (the precise-location row covers it).

### Government apps
**No**

### Financial features
**My app doesn't provide any financial features**

### Health apps
**My app does not have any health features**

### Advertising ID
**No.** The app does not use the advertising ID, and the release manifest does not declare
`com.google.android.gms.permission.AD_ID`.

## 4. Store listing

Copy the text from `listing.md`, then upload:

- App icon: `graphics/icon-512.png`
- Feature graphic: `graphics/feature-graphic.jpg`
- **Phone screenshots: at least 2**, ideally 4–6. I couldn't take these because they need a real
  account with real-looking data. Sign in on a phone with the reviewer account and capture
  Dashboard, My Tasks, a task with comments, Approvals, and dark mode. Avoid real employee names
  or sensitive project details, because screenshots are public.

## 5. Release

1. **Testing → Internal testing → Create new release.** Upload `WMT-1.1.0.aab` and accept Play App Signing (Google-managed key).
2. Release notes (en-US):
   ```
   First release on Google Play.
   ```
3. Install it from the internal testing link on a real phone. Sign in and open every main screen. Release builds are shrunk with R8, and a stripped class only fails when a screen first loads its data, so this is the check that catches it.
4. Watch the **pre-launch report** (Quality → Pre-launch report) for crashes. Its robot can't sign in unless you give it the reviewer credentials under *Pre-launch report → Settings → Test account*.
5. Promote to **Closed testing** (required for new personal accounts, see section 1) or **Production**, then click **Send for review**. A first review usually takes from a few days to about a week.

## Building the bundle again

```
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`, signed with the upload key via
`keystore.properties`. Raise `versionCode` in `app/build.gradle.kts` for every upload, because
Play refuses a code it has already seen.
