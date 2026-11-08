# Aum Private Gallery — รุ่นทดลอง

Fork ของ Round Sync v2.5.6 สำหรับ gallery วิดีโอที่เก็บบน rclone crypt/Google Drive โดยใช้บัญชีและ vault ของผู้ใช้เอง ไม่มี backend เพิ่ม รายงานผล build จริงอยู่ที่ [docs/TEST_REPORT.md](docs/TEST_REPORT.md) และข้อจำกัดอยู่ที่ [docs/SECURITY_NOTES.md](docs/SECURITY_NOTES.md)

## ติดตั้งและเริ่มใช้

1. ใช้ APK `ossDebug` สำหรับ `arm64-v8a` ที่ได้จาก build เท่านั้น ตรวจ SHA-256 เทียบกับไฟล์ SHA256SUMS ก่อนติดตั้ง อนุญาตติดตั้งจากแหล่งที่ใช้เปิด APK ตามที่ Android ขอ รุ่นนี้เป็น debug signed สำหรับทดสอบส่วนตัว application ID คือ `com.aum.privategallery.debug` ติดตั้งคู่กับ Round Sync ได้
2. เปิดแอป ผ่าน onboarding เดิม แล้วใช้หน้า Remotes สร้าง Google Drive remote ใหม่ เช่น `drive` ใช้โฟลเดอร์ทดลองที่แยกจากข้อมูลจริง ห้ามใช้การ sync/delete กับข้อมูลสำคัญระหว่างทดสอบ
3. สร้าง OAuth Client ID ของตนเองตาม [เอกสาร rclone](https://rclone.org/drive/#making-your-own-client-id) แล้วกรอก `client_id` และ `client_secret` ผ่านตัวตั้งค่า remote ของแอป ไม่ต้องส่งรหัส/ไฟล์ config เข้าแชท Shared Drive Client ID ของ rclone กำลังเลิกใช้ในปี 2026 จึงไม่ควรพึ่งค่า shared เดิม สำหรับ vault ใหม่ให้พิจารณา scope `drive.file` และให้แอปสร้างโฟลเดอร์เอง ใช้ OAuth client เดียวกันทุกเครื่อง Scope นี้อาจมองไม่เห็นไฟล์เดิมที่สร้างผ่านเว็บหรือ client อื่น หากจำเป็นต้องใช้สิทธิ์กว้างขึ้นให้อ่านคำอธิบายสิทธิ์ก่อนอนุญาต
4. ถ้า OAuth ผ่านมือถือไม่สำเร็จ ให้ตั้งค่าด้วย rclone บนคอมพิวเตอร์ที่เชื่อถือได้ ใช้ client ID ของตนเองและ authorize ตามคำแนะนำ rclone แล้วนำ config เข้าแอปผ่านเมนู import ของ Round Sync หลีกเลี่ยงส่ง config ผ่านบริการสาธารณะ และลบไฟล์ส่งต่อหลังนำเข้าสำเร็จ การ login ด้วย identity ใหม่นี้ยังต้องทดสอบกับบัญชีจริง
5. สร้าง remote ชนิด `crypt` เช่น `vault` โดยตั้ง remote path เป็น `drive:AumPrivateGallery-TestVault` ใช้ filename encryption `standard` และ directory name encryption เปิดไว้ ตั้ง crypt password และ salt ของตนเอง เก็บสำรองใน password manager หากลืมจะกู้ข้อมูลไม่ได้ บนอุปกรณ์อื่นต้องใช้ค่าเดียวกัน
6. เปิดเมนู **Private Video Library** เลือก `vault (crypt)` เท่านั้น Gallery จะไม่เสนอ remote ที่ไม่ได้เป็น crypt จากนั้นกด **+ Add video** เลือก MP4/H.264/AAC สั้น ๆ เพื่อเริ่มทดสอบ เปิดหน้านี้ค้างไว้จนเสร็จ
7. Thumbnail/title/duration และสถานะ Cloud/Offline จะแสดงจาก catalog/preview โดยไม่ดาวน์โหลดวิดีโอทั้งไฟล์ แตะเพื่อ stream; กดค้างแล้วเลือก **Download for offline playback** เพื่อเก็บบนเครื่อง หรือ **Remove offline copy** เพื่อลบเฉพาะในเครื่อง **Cancel transfer** ขอหยุดงานที่กำลังทำ ส่วน **Clear local copies** ลบ cache และวิดีโอ offline ของ remote นี้เท่านั้น ไม่ลบ cloud

## ข้อจำกัดที่ต้องทราบ

- Catalog และ thumbnail cache ใช้ Android Keystore encryption ส่วนวิดีโอ offline ยังเป็น plaintext ในพื้นที่ส่วนตัวของแอป มีคำอธิบายก่อนดาวน์โหลด ไม่ใช่ local vault ที่เข้ารหัสครบทุกส่วน
- ไม่มี biometric lock; rclone.conf ยังใช้การเก็บ credential แบบ upstream ปิด logging และไม่เผยแพร่ไฟล์ config
- การเพิ่มจากหลายเครื่องใช้ metadata แยก UUID จึงไม่เขียนทับ catalog กลาง รุ่นนี้ไม่รองรับแก้ title/tag หรือลบวิดีโอใน cloud Catalog v1 อ่านได้ แต่ไม่แก้ไขและไม่ migrate ด้วยการเขียนทับ
- Upload/download ผูกกับหน้า Activity การหมุนจอ/ปิดแอป/ระบบฆ่าแอปอาจหยุดงาน Retry อาจเหลือไฟล์ orphan บน cloud ระบบจะไม่ลบทิ้งอัตโนมัติ
- การ refresh ไล่ metadata แยกรายการ ยังต้องปรับ incremental sync สำหรับ library ใหญ่ ไม่มีข้อมูล benchmark ที่วัดแล้วบนโทรศัพท์
- Source pin rclone 1.68.0 ตาม baseline เก่า การรองรับ OAuth/Android16/16 KiB page size/codec และการ seek บน POCO X7 Pro ยังต้องทดสอบตาม [checklist](docs/DEVICE_TEST_CHECKLIST.md) ก่อนใช้ข้อมูลจริง
- Debug signing key สร้างเฉพาะบนเครื่อง/CI ไม่อยู่ใน source การ build คนละเครื่องอาจต้องถอนรุ่นเดิมก่อนติดตั้ง ทำให้ข้อมูล local หาย จึงต้องสำรอง crypt credentials ของตนเองเสมอ ไม่มี release signing key ฝังมาให้

## Build และ license

ดาวน์โหลด APK ที่ build แล้วจาก [Releases](https://github.com/SakdaRt/Aum-Private-Gallery/releases) พร้อม SHA256SUMS ส่วนวิธี build อยู่ที่ [docs/BUILD_ANDROID.md](docs/BUILD_ANDROID.md) ไฟล์ตั้งค่า GitHub Actions เตรียมไว้ที่ `ci/android.yml` และยังไม่เปิดทำงาน เพราะสิทธิ์บัญชีที่ใช้ push ไม่มี `workflow`

GPLv3 ตาม [LICENSE](LICENSE) และเครดิต Round Sync/RCX เดิมใน source/README เดิม เมื่อนำ APK ไปแจกต้องส่งมอบ corresponding source และ license ด้วย ไม่มีการใช้ signing identity ของ upstream และไม่มี credentials ของผู้ใช้ฝังใน repository
