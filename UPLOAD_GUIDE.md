# Mission 2027 - upload aur install guide

## A. GitHub par upload (Codespaces, sabse aasan)
1. github.com/sure1234-del/Home-launcher- kholo, Code > Codespaces > repo ka Codespace kholo.
2. Zip (Mission2027-fixed.zip) ko Codespace ke file panel me drag-drop karo, phir terminal me:
   unzip -o Mission2027-fixed.zip && rm Mission2027-fixed.zip
3. git add -A && git commit -m "Mission 2027: toolkit, automation, 4K Sangam wallpaper" && git push
   (assets/wallpaper ki 17 files lagbhag 59 MB hain, har file 4 MB se chhoti, GitHub limit se bahut neeche.)

## B. Bina Codespace (sirf browser)
1. Repo > Add file > Upload files. Zip ko khol kar `src` aur `.github` folders drag karo (folder drag chalta hai).
2. Ek baar me 100 files se zyada na ho; assets/wallpaper ke liye alag upload kar sakte ho.
3. Commit to main.

## C. APK ban kar aana
1. Repo > Actions > "Build APK" chalta dikhega (push par apne aap). 8-12 minute.
2. Green hone par Releases > "latest" > .apk download karo. APK ka size lagbhag 60-70 MB hoga.

## D. Install
1. Pehli baar purani Mission app uninstall karo (naya fixed signing key). Uske baad update seedha upar install hoga.
2. Install unknown apps allow karo.
3. Home launcher chuno (Settings > Apps > Default apps > Home).
4. Permissions do: Usage access, Display over other apps, Accessibility (Circle to Search), Alarms, Notifications.
5. HyperOS: Autostart ON, Battery: No restrictions, Notifications: floating/pop-up ON, Accessibility ke liye "Restricted settings" allow (App info > 3-dot menu).
6. Settings > Zite me apna key daalo. Reset password device par khud set karo.
7. Wallpaper: Settings > Wallpaper > Scene "Prayagraj Sangam (4K, live)" > Apply now.

## E. Aage ke updates
Repo me push karo, Action ban jaye, app me Tools card > "Update app" dabao.
