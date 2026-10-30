const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");

let env;
const A = "alice";
const B = "bob";
const X = "mallory";

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "gps-tracker-rules-test",
    firestore: { rules: fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8") },
  });
});
after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

const db = (uid) => (uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore());
const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
const loc = { lat: -6.2, lng: 106.8, speed: 0, updatedAt: 1 };

// Meniru Repo.invite(): pengirim membuat entri sendiri (pending_out) dan entri penerima (pending_in).
async function invite(from, to) {
  await assertSucceeds(db(from).doc(`users/${from}/friends/${to}`).set({ name: to, status: "pending_out" }));
  await assertSucceeds(db(from).doc(`users/${to}/friends/${from}`).set({ name: from, status: "pending_in" }));
}
// Meniru Repo.accept(): penerima menandai entri pengirim dulu, lalu entrinya sendiri.
async function accept(receiver, sender) {
  await assertSucceeds(db(receiver).doc(`users/${sender}/friends/${receiver}`).update({ status: "accepted" }));
  await assertSucceeds(db(receiver).doc(`users/${receiver}/friends/${sender}`).update({ status: "accepted" }));
}

test("tanpa login semuanya ditolak", async () => {
  await seed((d) => d.doc(`users/${A}`).set({ name: "A" }));
  await assertFails(db(null).doc(`users/${A}`).get());
  await assertFails(db(null).doc(`locations/${A}`).get());
});

test("profil: login boleh baca, hanya pemilik boleh tulis", async () => {
  await assertSucceeds(db(A).doc(`users/${A}`).set({ name: "A" }));
  await assertSucceeds(db(B).doc(`users/${A}`).get());
  await assertFails(db(B).doc(`users/${A}`).update({ name: "hack" }));
});

test("lokasi: hanya pemilik yang menulis", async () => {
  await assertSucceeds(db(A).doc(`locations/${A}`).set(loc));
  await assertFails(db(X).doc(`locations/${A}`).set(loc));
});

test("orang asing tidak bisa membaca lokasi", async () => {
  await seed((d) => d.doc(`locations/${A}`).set(loc));
  await assertFails(db(X).doc(`locations/${A}`).get());
});

test("undangan belum diterima: belum bisa baca lokasi", async () => {
  await invite(A, B);
  await seed((d) => d.doc(`locations/${A}`).set(loc));
  await assertFails(db(B).doc(`locations/${A}`).get());
});

test("alur undang lalu terima: kedua pihak saling melihat", async () => {
  await invite(A, B);
  await accept(B, A);
  await seed(async (d) => {
    await d.doc(`locations/${A}`).set(loc);
    await d.doc(`locations/${B}`).set(loc);
  });
  await assertSucceeds(db(B).doc(`locations/${A}`).get());
  await assertSucceeds(db(A).doc(`locations/${B}`).get());
  await assertFails(db(X).doc(`locations/${A}`).get());
});

test("SERANGAN: orang asing tidak bisa memberi dirinya akses (self-grant)", async () => {
  await seed((d) => d.doc(`locations/${A}`).set(loc));
  // X membuat undangan palsu di daftar A, dan entri pendukung di daftarnya sendiri.
  await assertSucceeds(db(X).doc(`users/${A}/friends/${X}`).set({ name: X, status: "pending_in" }));
  await assertSucceeds(db(X).doc(`users/${X}/friends/${A}`).set({ name: A, status: "pending_in" }));
  // Mencoba langsung membuat accepted ditolak.
  await assertFails(db(X).doc(`users/${A}/friends/${B}`).set({ name: X, status: "accepted" }));
  // Menaikkan pending_in menjadi accepted ditolak.
  await assertFails(db(X).doc(`users/${A}/friends/${X}`).update({ status: "accepted" }));
  await assertFails(db(X).doc(`locations/${A}`).get());
});

test("SERANGAN: tidak bisa membuat entri accepted langsung di daftar orang lain", async () => {
  await assertFails(db(X).doc(`users/${A}/friends/${X}`).set({ name: X, status: "accepted" }));
});

test("daftar teman hanya bisa dibaca pemiliknya", async () => {
  await invite(A, B);
  await assertSucceeds(db(A).doc(`users/${A}/friends/${B}`).get());
  await assertFails(db(X).doc(`users/${A}/friends/${B}`).get());
});

test("salah satu pihak bisa menghapus pertemanan dan akses hilang", async () => {
  await invite(A, B);
  await accept(B, A);
  await seed((d) => d.doc(`locations/${A}`).set(loc));
  await assertSucceeds(db(B).doc(`users/${A}/friends/${B}`).delete());
  await assertSucceeds(db(B).doc(`users/${B}/friends/${A}`).delete());
  await assertFails(db(B).doc(`locations/${A}`).get());
});

test("menolak undangan: penerima bisa menghapus kedua entri", async () => {
  await invite(A, B);
  await assertSucceeds(db(B).doc(`users/${B}/friends/${A}`).delete());
  await assertSucceeds(db(B).doc(`users/${A}/friends/${B}`).delete());
});

// ---- Tahap 2: tempat, perjalanan, peristiwa, favorit/grup ----

test("tempat: hanya pemilik yang bisa baca/tulis", async () => {
  await assertSucceeds(db(A).doc(`users/${A}/places/home`).set({ name: "Rumah", lat: 1, lng: 2, radius: 100 }));
  await assertSucceeds(db(A).doc(`users/${A}/places/home`).get());
  await invite(A, B);
  await accept(B, A);
  await assertFails(db(B).doc(`users/${A}/places/home`).get());
  await assertFails(db(X).doc(`users/${A}/places/home`).set({ name: "x" }));
});

for (const col of ["trips", "events"]) {
  test(`${col}: pemilik menulis, teman accepted membaca, orang asing ditolak`, async () => {
    await assertSucceeds(db(A).doc(`users/${A}/${col}/t1`).set({ ts: 1 }));
    await assertFails(db(X).doc(`users/${A}/${col}/t2`).set({ ts: 1 }));
    await assertFails(db(X).doc(`users/${A}/${col}/t1`).get());
    await invite(A, B);
    await assertFails(db(B).doc(`users/${A}/${col}/t1`).get()); // belum diterima
    await accept(B, A);
    await assertSucceeds(db(B).doc(`users/${A}/${col}/t1`).get());
    await assertFails(db(B).doc(`users/${A}/${col}/t3`).set({ ts: 1 })); // teman tidak boleh menulis
    await assertFails(db(B).doc(`users/${A}/${col}/t1`).delete());
  });

  test(`${col}: query koleksi teman accepted berjalan, orang asing ditolak`, async () => {
    await invite(A, B);
    await accept(B, A);
    await seed((d) => d.doc(`users/${A}/${col}/t1`).set({ ts: 5 }));
    await assertSucceeds(db(B).collection(`users/${A}/${col}`).orderBy("ts").limit(10).get());
    await assertFails(db(X).collection(`users/${A}/${col}`).orderBy("ts").limit(10).get());
  });
}

test("favorit/grup: hanya pemilik daftar yang bisa mengubah", async () => {
  await invite(A, B);
  await accept(B, A);
  await assertSucceeds(db(A).doc(`users/${A}/friends/${B}`).update({ favorite: true, group: "Keluarga" }));
  // B tidak boleh mengubah label di daftar A (hanya update pending_out -> accepted yang diizinkan)
  await assertFails(db(B).doc(`users/${A}/friends/${B}`).update({ favorite: true }));
  await assertFails(db(X).doc(`users/${A}/friends/${B}`).update({ favorite: true }));
});

// ---- Tahap 3: chat ----

const chatId = (x, y) => [x, y].sort().join("_");
const msg = (from, extra = {}) => ({ from, type: "text", text: "halo", ts: 1, ...extra });

test("chat: teman accepted bisa kirim dan baca", async () => {
  await invite(A, B);
  await accept(B, A);
  const id = chatId(A, B);
  await assertSucceeds(db(A).collection(`chats/${id}/messages`).add(msg(A)));
  await assertSucceeds(db(B).collection(`chats/${id}/messages`).add(msg(B, { type: "ping", text: "" })));
  await assertSucceeds(db(B).collection(`chats/${id}/messages`).orderBy("ts", "desc").limit(50).get());
});

test("chat: orang asing dan pending ditolak", async () => {
  const id = chatId(A, B);
  await assertFails(db(X).collection(`chats/${id}/messages`).add(msg(X)));
  await assertFails(db(X).collection(`chats/${id}/messages`).get());
  await invite(A, B); // belum diterima
  await assertFails(db(A).collection(`chats/${id}/messages`).add(msg(A)));
  await assertFails(db(B).collection(`chats/${id}/messages`).get());
});

test("chat: pihak ketiga tidak bisa masuk walau berteman dengan salah satu", async () => {
  await invite(A, B); await accept(B, A);
  await invite(A, X); await accept(X, A);
  await assertFails(db(X).collection(`chats/${chatId(A, B)}/messages`).get());
});

test("chat: from palsu, tipe asing, dan audio terlalu besar ditolak", async () => {
  await invite(A, B); await accept(B, A);
  const col = (u) => db(u).collection(`chats/${chatId(A, B)}/messages`);
  await assertFails(col(A).add(msg(B)));
  await assertFails(col(A).add(msg(A, { type: "sos" })));
  await assertFails(col(A).add(msg(A, { type: "voice", audio: "x".repeat(700001) })));
  await assertSucceeds(col(A).add(msg(A, { type: "voice", audio: "x".repeat(1000), durMs: 3000 })));
  await assertFails(col(A).add(msg(A, { text: "x".repeat(1001) })));
});

test("chat: update dilarang, hapus hanya pengirim", async () => {
  await invite(A, B); await accept(B, A);
  const path = `chats/${chatId(A, B)}/messages/m1`;
  await seed((d) => d.doc(path).set(msg(A)));
  await assertFails(db(A).doc(path).update({ text: "edit" }));
  await assertFails(db(B).doc(path).delete());
  await assertSucceeds(db(A).doc(path).delete());
});

test("chat: setelah pertemanan dihapus, akses hilang", async () => {
  await invite(A, B); await accept(B, A);
  const id = chatId(A, B);
  await seed((d) => d.doc(`chats/${id}/messages/m1`).set(msg(A)));
  await assertSucceeds(db(B).doc(`users/${B}/friends/${A}`).delete());
  await assertFails(db(B).doc(`chats/${id}/messages/m1`).get());
});

test("peristiwa SOS dengan koordinat dibaca teman accepted saja", async () => {
  await invite(A, B); await accept(B, A);
  await assertSucceeds(db(A).doc(`users/${A}/events/s1`).set({ type: "sos", lat: 1, lng: 2, ts: 5, fromName: "A", placeName: "" }));
  await assertSucceeds(db(B).doc(`users/${A}/events/s1`).get());
  await assertFails(db(X).doc(`users/${A}/events/s1`).get());
});
