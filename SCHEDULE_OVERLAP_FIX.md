# Schedule overlap fix

The scrolling list could paint outside its viewport because outer layout containers allow drawing beyond their bounds for shadows. At a nonzero scroll offset, cards and the sample caption could cover Create Service, Calendar / Manage Availability, and the filter tabs.

StableScrollView now applies explicit local clip bounds whenever its size changes, including rotation and keyboard resizing. Child and padding clipping are enabled. Both Schedule page roots also clip their children. The list remains below the existing fixed controls and above the bottom navigation; colors, spacing, filters, service actions, scrolling physics and metronome/chord features are retained.

Open Synced-N in Android Studio and keep your own local.properties SDK path. Run the app, open Schedule, choose All, and scroll/fling from top to bottom. Cards and the sample caption should stay inside the list viewport without covering the controls. Check both leader and member accounts, rotation and larger text settings.

Validation: checked both schedule builders and shared scroll viewport clipping, and verified other project files are unchanged. Android build and on-device rendering have not been run in this environment.
