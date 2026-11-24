# Spesifikasi Perhitungan Gaji

## 1. Data Karyawan

- Sediakan menu **Data Karyawan** untuk menyimpan nama karyawan yang digunakan berulang kali.
- Pengguna wajib mengisi minimal satu nama di **Data Karyawan** sebelum membuat perhitungan gaji. Jika data masih kosong, arahkan pengguna ke menu tersebut.
- Setelah pengguna menyimpan tanggal dan jam perhitungan, tampilkan **Daftar Karyawan** dan langsung muat nama dari **Data Karyawan**.
- Sediakan tombol **+** di bawah daftar untuk menambah nama khusus pada perhitungan tersebut. Nama tambahan hanya tersimpan di perhitungan yang sedang dibuka dan tidak masuk ke **Data Karyawan**.
- Simpan daftar nama bersama perhitungannya agar saat perhitungan dibuka ulang, nama tambahan tetap muncul dan daftar yang sudah disimpan tidak berubah ketika data master diperbarui.

## 2. Alur Perhitungan Gaji

1. Pengguna membuka **Perhitungan Gaji** dan memilih **Buat Perhitungan**.
2. Sistem menampilkan kolom tanggal, bulan, tahun, dan jam. Nilai awal mengikuti tanggal dan jam lokal perangkat. Pengguna bisa mengubahnya sebelum melanjutkan.
3. Setelah tanggal dan jam diisi, sistem membuka **Daftar Karyawan** untuk perhitungan tersebut. Pengguna dapat menambahkan nama khusus melalui tombol **+**.
4. Setelah daftar berisi minimal satu nama, pengguna bisa membuka **Tabel Perhitungan**.
5. Dari tabel, tombol **Kembali** membuka **Daftar Karyawan** milik perhitungan yang sama. Nama dan angka yang sudah diisi tetap tersimpan.
6. Setelah pengisian selesai, tombol **Simpan Total** menyimpan perhitungan dan menampilkannya di **Riwayat**. Hasilnya ikut masuk ke tab **Total**.

## 3. Pengaturan Rumus

### Kontrol di setiap daftar perhitungan

- Tambahkan kontrol **Ubah Rumus** di setiap daftar perhitungan.
- Rumus bawaan adalah **÷ 12** dalam keadaan aktif.
- Tombol operasi berfungsi sebagai sakelar. Saat **÷ 12** ditekan untuk menonaktifkan rumus, angka **12** berubah menjadi abu-abu dan operasi dilewati. Tekan lagi untuk mengaktifkannya dengan pengaturan terakhir.
- Di **Ubah Rumus**, pengguna bisa memilih operasi **bagi (÷)**, **kali (×)**, **tambah (+)**, atau **kurang (−)**, lalu mengubah nilainya. Contohnya **÷ 10**, **− 50.000**, atau **+ 25.000**.
- Jika operasi bagi aktif, nilai pembagi wajib lebih besar dari nol.

### Cakupan perubahan

| Cakupan | Perilaku |
| --- | --- |
| **Per daftar** | Pengaturan berlaku untuk semua karyawan dalam daftar perhitungan yang sedang dibuka. |
| **Global** | Pengaturan menjadi bawaan untuk perhitungan baru dan berlaku untuk daftar yang memakai pengaturan global. |
| **Gunakan Rumus Global** | Mengembalikan daftar yang punya pengaturan sendiri agar kembali mengikuti rumus global. |

Status aktif, jenis operasi, dan angka merupakan bagian dari pengaturan rumus. Nol pada kolom **Nilai** untuk operasi selain bagi tetap dihitung sebagai nilai yang diisi.

## 4. Potongan dan Penambahan

- Pengguna dapat menambahkan beberapa kolom penyesuaian pada tabel perhitungan.
- Setiap kolom memiliki **Nama**, **Operasi** (**+** atau **−**), dan **Nominal** yang dapat diisi per karyawan. Contohnya **Bonus (+)** atau **Kasbon (−)**.
- Kolom yang ditambahkan tersedia untuk semua baris karyawan dalam daftar tersebut. Nominal masing-masing karyawan diisi secara terpisah.
- Sel yang belum diisi tetap kosong dan dilewati dalam perhitungan. Jika pengguna memasukkan angka **0**, tampilkan angka **0**.

## 5. Penghitungan dan Pembaruan Total

1. Ambil nilai dasar gaji karyawan dari tabel.
2. Terapkan rumus yang berlaku jika aktif. Jika dinonaktifkan, gunakan nilai dasar gaji.
3. Terapkan penambahan dan potongan yang terisi untuk karyawan tersebut.
4. Jumlahkan hasil semua karyawan untuk mendapatkan total perhitungan.
5. Jumlahkan total seluruh perhitungan yang sudah disimpan untuk mendapatkan hasil di tab **Total**.

Perhitungan yang sudah disimpan tetap dapat diedit. Setelah perubahan disimpan, sistem menghitung ulang hasil setiap karyawan yang terdampak, total perhitungan, dan total seluruh gaji. Perubahan memperbarui entri riwayat yang sama, sehingga total tidak menghitung perhitungan tersebut dua kali. Perubahan rumus global juga menghitung ulang perhitungan tersimpan yang masih mengikuti rumus global.

## 6. Riwayat dan Tab Total

### Riwayat

- Tampilkan satu entri per perhitungan yang disimpan, dengan label hari, tanggal, bulan, dan tahun. Contoh **Selasa, 24 November 2026**.
- Saat entri dipilih, buka **Daftar Karyawan** yang tersimpan pada perhitungan tersebut. Dari sana, pengguna bisa masuk lagi ke tabel perhitungan.
- Tanggal dan jam yang dipilih saat membuat perhitungan tetap tersimpan bersama entri.

### Total

- Tampilkan total seluruh perhitungan gaji yang sudah disimpan.
- Perbarui nilai ketika perhitungan baru disimpan atau data yang sudah disimpan berubah.

## 7. Kriteria Penerimaan

- Jika **Data Karyawan** kosong, pengguna diarahkan untuk menambah nama sebelum membuat perhitungan.
- Setelah tanggal dan jam diisi, nama dari **Data Karyawan** langsung muncul. Nama dari tombol **+** tetap tersedia saat perhitungan dibuka dari **Riwayat** dan tidak muncul di **Data Karyawan**.
- Dengan nilai dasar **120.000**, rumus aktif **÷ 12** menghasilkan **10.000** sebelum penyesuaian. Saat rumus dimatikan, angka **12** berwarna abu-abu dan hasilnya kembali menjadi **120.000**.
- Perubahan rumus per daftar hanya memengaruhi daftar itu. Perubahan rumus global memperbarui daftar yang mengikutinya. Daftar dengan rumus sendiri tetap menggunakan pengaturannya sampai pengguna memilih **Gunakan Rumus Global**.
- Dengan hasil awal **10.000**, bonus **2.000**, dan potongan **500**, total karyawan menjadi **11.500**. Kolom nominal yang kosong tetap kosong dan tidak mengubah hasil.
- Mengedit perhitungan yang sudah disimpan memperbarui totalnya dan tab **Total**, dengan jumlah entri riwayat tetap sama.
