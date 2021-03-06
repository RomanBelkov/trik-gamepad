namespace com.trik.gamepad
open Android.Preferences
open Android.App
open Android.Content

[<Activity(Label="@string/title_activity_settings", ScreenOrientation=PM.ScreenOrientation.FullSensor)>]
type SettingsActivity() =
    inherit PreferenceActivity()
        
    static member val SK_HOST_ADDRESS = "hostAddress"
    static member val SK_HOST_PORT    = "hostPort"
    static member val SK_SHOW_PADS    = "showPads"
    static member val SK_VIDEO_URI    = "videoURI"
    static member val SK_WHEEL_SENSITIVITY = "wheelSens"

    override this.OnCreate savedInstanceState =
        base.OnCreate savedInstanceState
        this.AddPreferencesFromResource Resource_Xml.pref_general
