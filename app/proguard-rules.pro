# Keep all Fragment classes, which are often instantiated via reflection from XML.
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}

# Keep all PreferenceFragmentCompat classes and their constructors.
-keep public class * extends androidx.preference.PreferenceFragmentCompat {
    public <init>();
}

# A more specific rule for your project structure to be safe.
-keep public class cu.axel.smartdock.fragments.** { *; }
