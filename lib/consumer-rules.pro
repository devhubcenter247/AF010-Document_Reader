# Consumer ProGuard rules for the :lib module.
# These rules are applied to any app module that depends on this library.
# Add keep rules here if classes in :lib are accessed via reflection by consumers.

# BaseFragment checks by name whether a fragment overrides onBackPressed() to decide if it
# needs a back callback; keep the name so R8 renaming can't make every lookup miss.
-keepclassmembers class com.ui.baselib.base.BaseFragment {
    public boolean onBackPressed();
}
-keepclassmembers class * extends com.ui.baselib.base.BaseFragment {
    public boolean onBackPressed();
}
