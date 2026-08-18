# Safe Screen

**An AI-powered real-time system for detecting and protecting against harmful content**

---

## About the Project

**Safe Screen** is an innovative application that analyzes images and videos displayed on a user's device screen in real time. Using Computer Vision technologies, the system detects inappropriate (immoral or harmful) content and protects users by automatically blurring or blocking it.

Unlike existing content-filtering systems, Safe Screen does not operate only at the internet or application level. Instead, it works **directly at the screen level**, making it possible to monitor content regardless of its source.

---

## Author

|                |                                                                   |
| -------------- | ----------------------------------------------------------------- |
| **Name**       | Muminov Dilmurod Yunus o‘g‘li                                     |
| **University** | Urgench State University named after Abu Rayhan Beruni            |
| **Email**      | [dilmurod21muminov@gmail.com](mailto:dilmurod21muminov@gmail.com) |

---

## Technologies

* **Mobile App (Android):** Kotlin, Jetpack Compose
* **Mobile App (iOS):** Swift, SwiftUI
* **Backend:** FastAPI
* **AI Module:** Python, PyTorch

---

## Project Relevance

Today, more than 5 billion people worldwide use the internet, and a large proportion of minors access the digital environment without parental supervision. According to research, the majority of people under the age of 18 have been exposed to inappropriate images and videos, which can negatively affect their psychological well-being, social relationships, and worldview.

Existing solutions such as SafeSearch, DNS filtering, and parental control applications primarily operate at the internet level and cannot provide complete protection at the screen level. Therefore, there is a need for a real-time, universal, and effective solution.

---

## Project Goal

> To create a healthy psychological and social environment and promote safe digital usage by protecting young people from harmful visual content.

---

## Innovation

* Works directly at the **screen level**
* Uses AI to analyze images in **real time**
* Automatically **blurs or blocks** harmful content
* Provides a **universal solution** for all platforms, including the internet, applications, and video content

---

## How the System Works

1. The screen is continuously monitored
2. The AI model detects harmful content
3. Detected content is automatically blurred or blocked
4. When necessary, a notification is sent to the parent

---

## Expected Results

* Young people are effectively protected from harmful content
* Psychological well-being and social stability are improved
* Attention and learning efficiency are increased
* The effectiveness of parental supervision is enhanced

---

## Additional Features

The system also provides additional monitoring capabilities for parents. Parents can receive notifications about activities occurring on their child's device.

The key advantage of the system is that it does not analyze only internet content, but **all visual information appearing on the device screen**. This makes it more comprehensive than conventional content-filtering systems.

Yes. Based on your current implementation, I would add a section that describes the **actual current MVP**, rather than describing features that are only planned.

## Current Version — How It Works

The current version of **Safe Screen** is an Android application that uses an offline AI model to monitor the device screen and detect potentially harmful visual content.

The application works continuously in the foreground and captures screen content at regular intervals rather than analyzing every individual frame. In the current implementation, the screen is processed approximately **once per second**, which significantly reduces CPU and battery consumption compared with continuous frame-by-frame analysis.

The captured screen image is processed locally using an **offline image-classification model**, so the content does not need to be uploaded to a remote server for detection. When the model determines that harmful content is present, Safe Screen automatically displays a protective overlay over the detected content.

### Current Workflow

1. Safe Screen runs as a foreground Android service.
2. The application periodically captures the current screen content.
3. The captured image is passed to the **offline AI classification model**.
4. The model analyzes the image and determines whether potentially harmful content is present.
5. If harmful content is detected, Safe Screen displays an **overlay that covers the screen content**.
6. The process repeats continuously while the protection service is active.

### Current Architecture

**Android → Screen Capture → Image Preprocessing → Offline AI Model → Classification → Protective Overlay**

This approach allows Safe Screen to work independently of the application or website currently being used. In principle, the same protection mechanism can monitor visual content displayed in browsers, social media applications, video platforms, and other Android applications.

### Current Limitations

The current version is an **MVP** and still has several areas for improvement. The AI model and screen-processing pipeline need further optimization to reduce CPU, RAM, and battery usage while maintaining detection accuracy. Additional features such as more advanced parental controls, cloud-based synchronization, and premium functionality are planned for future versions.



---

## Future Plans

* [ ] Implement the system in educational institutions
* [ ] Transform it into a global platform
* [ ] Further improve the AI model

---

## Installation

[Install Safe Screen via Google Play](https://play.google.com/store/apps/details?id=com.dilmurod.safescreen&hl=en&pli=1)

---

## License
© Muminov Dilmurod Yunus o'g'li