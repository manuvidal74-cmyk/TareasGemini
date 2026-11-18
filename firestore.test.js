const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTaskPayload(userId, now = new Date(Date.now() - 5000)) {
  return {
    userId,
    titulo: "Revisar agenda diaria",
    tipo: "tarea",
    relevancia: "alta",
    repeticion: "diaria",
    fecha: "2026-11-18",
    hora: "09:00",
    notas: "- [ ] Revisar correo",
    completada: false,
    traspasado: false,
    fechaCreacion: "2026-11-18T08:00:00",
    fechaCompletada: "",
    notaCierre: "",
    notificacionActiva: true,
    minutosAntes: 10,
    rachaDiaria: 3,
    ultimaFechaCompletadaDiaria: "",
    categoria: "Trabajo",
    diasSemana: "1,2,3,4,5",
    createdAt: now,
    updatedAt: now,
  };
}

function validDailyNotePayload(userId, now = new Date(Date.now() - 5000)) {
  return {
    userId,
    fecha: "2026-11-18",
    texto: "Recordar llamar a la gestoría",
    createdAt: now,
    updatedAt: now,
  };
}

function validDiaryEntryPayload(userId, now = new Date(Date.now() - 5000)) {
  return {
    userId,
    fecha: "2026-11-18",
    hora: "10:15",
    fechaHoraISO: "2026-11-18T10:15:00",
    tipo: "manual",
    tareaId: "",
    tituloTarea: "",
    texto: "[10:15] Reunión completada con éxito.",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write tasks, daily_notes, or diary_entries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/tasks`)
      .doc("t-1")
      .set(validTaskPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/daily_notes`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/daily_notes`)
      .doc("2026-11-18")
      .set(validDailyNotePayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/diary_entries`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/diary_entries`)
      .doc("d-1")
      .set(validDiaryEntryPayload(ALICE_UID))
  );
});

test("Authenticated owner: valid CloudTask create, partial update, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("t-1731920000-ab12");

  await assertSucceeds(docRef.set(validTaskPayload(ALICE_UID)));

  await assertSucceeds(
    docRef.update({
      titulo: "Revisar agenda y correo prioritario",
      completada: true,
      fechaCompletada: "2026-11-18T11:00:00",
      notaCierre: "Todo al día",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const listSnap = await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/tasks`).get()
  );
  if (listSnap.size !== 1) {
    throw new Error(`Expected 1 task, got ${listSnap.size}`);
  }

  await assertSucceeds(docRef.delete());
});

test("Authenticated owner: valid CloudDailyNote and CloudDiaryEntry create, update, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.collection(`users/${ALICE_UID}/daily_notes`).doc("2026-11-18");
  const diaryRef = aliceDb.collection(`users/${ALICE_UID}/diary_entries`).doc("d-1731920000-cd34");

  await assertSucceeds(noteRef.set(validDailyNotePayload(ALICE_UID)));
  await assertSucceeds(
    noteRef.update({
      texto: "Nota actualizada del día",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/daily_notes`).get());
  await assertSucceeds(noteRef.delete());

  await assertSucceeds(diaryRef.set(validDiaryEntryPayload(ALICE_UID)));
  await assertSucceeds(
    diaryRef.update({
      texto: "[10:15] Reunión completada y enviada acta.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/diary_entries`).get());
  await assertSucceeds(diaryRef.delete());
});

test("Authenticated user cannot read, write, or delete another user's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb
      .collection(`users/${BOB_UID}/tasks`)
      .doc("bob-task-1")
      .set(validTaskPayload(BOB_UID));
    await adminDb
      .collection(`users/${BOB_UID}/daily_notes`)
      .doc("2026-11-18")
      .set(validDailyNotePayload(BOB_UID));
    await adminDb
      .collection(`users/${BOB_UID}/diary_entries`)
      .doc("bob-diary-1")
      .set(validDiaryEntryPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/tasks`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/tasks`).doc("bob-task-1").get());
  await assertFails(
    aliceDb
      .collection(`users/${BOB_UID}/tasks`)
      .doc("bob-task-1")
      .update({ titulo: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(aliceDb.collection(`users/${BOB_UID}/tasks`).doc("bob-task-1").delete());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/daily_notes`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/diary_entries`).get());
});

test("Security bounds reject extra id key, immutable createdAt/userId edits, invalid IDs, future timestamps, and invalid enums", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const validDoc = aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("t-valid-1");
  await assertSucceeds(validDoc.set(validTaskPayload(ALICE_UID)));

  // Reject immutable createdAt mutation
  await assertFails(
    validDoc.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable userId mutation
  await assertFails(
    validDoc.update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject extra @DocumentId 'id' field in create payload
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/tasks`)
      .doc("t-extra-id")
      .set({ ...validTaskPayload(ALICE_UID), id: "t-extra-id" })
  );

  // Reject invalid document ID with dots or spaces
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/tasks`)
      .doc("bad.task.id")
      .set(validTaskPayload(ALICE_UID))
  );

  // Reject future server timestamp spoofing
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/tasks`)
      .doc("t-future-ts")
      .set(validTaskPayload(ALICE_UID, new Date(Date.now() + 60000)))
  );

  // Reject invalid tipo enum
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/tasks`)
      .doc("t-bad-enum")
      .set({ ...validTaskPayload(ALICE_UID), tipo: "invalido" })
  );

  // Reject oversized title (> 300 chars)
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/tasks`)
      .doc("t-long-title")
      .set({ ...validTaskPayload(ALICE_UID), titulo: "A".repeat(301) })
  );
});
