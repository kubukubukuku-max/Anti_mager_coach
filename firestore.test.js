const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validWorkoutPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    sportType: "DUMBBELL",
    source: "MANUAL",
    startedAtIso: "2026-11-22T07:30:00Z",
    durationMin: 25,
    rpe: 6,
    notes: "Bodyweight dasar pagi",
    dumbbellItems: [
      { exerciseName: "Push-up", weightKg: 0.0, sets: 3, reps: 8 },
      { exerciseName: "Bodyweight Squat", weightKg: 0.0, sets: 3, reps: 12 },
    ],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validUserProfilePayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    themeMode: "warm_cream",
    displayName: "Alice Runner",
    email: "alice@example.com",
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

// --- UNAUTHENTICATED ACCESS TESTS ---

test("Unauthenticated user cannot read or write profiles or workouts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validUserProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("workouts").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workouts")
      .doc("workout-1")
      .set(validWorkoutPayload(ALICE_UID))
  );
});

// --- USER PROFILE SECURITY TESTS ---

test("Authenticated owner can create, read, and update own profile and theme", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validUserProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  // Theme-only partial update
  await assertSucceeds(
    profileRef.update({
      themeMode: "deep_night",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Listing /users collection is strictly denied (PII isolation)
  await assertFails(aliceDb.collection("users").get());

  // Invalid theme value rejected
  await assertFails(
    profileRef.update({
      themeMode: "neon_pink",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field injection rejected (Shadow Update Test)
  await assertFails(
    profileRef.update({
      themeMode: "warm_cream",
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt cannot be modified
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Cross-user access to another user's profile is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .set(validUserProfilePayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      themeMode: "deep_night",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).delete());
});

// --- WORKOUT SUBCOLLECTION SECURITY & KOTLIN PAYLOAD PARITY TESTS ---

test("Authenticated owner can list empty subcollection, create, update, and delete workouts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const workoutsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("workouts");

  // Empty subcollection list must succeed (matches observeWorkouts snapshots() query)
  await assertSucceeds(workoutsCol.get());

  // Create manual dumbbell workout (NO @DocumentId `id` field in body)
  const docRef = workoutsCol.doc("550e8400-e29b-41d4-a716-446655440000");
  await assertSucceeds(docRef.set(validWorkoutPayload(ALICE_UID)));

  // Create Health Connect synced workout with optional externalId, distanceKm, avgHr
  const hcDocRef = workoutsCol.doc("hc-run-2026-11-22");
  await assertSucceeds(
    hcDocRef.set(
      validWorkoutPayload(ALICE_UID, {
        externalId: "hc-record-abc-123",
        sportType: "RUNNING",
        source: "HEALTH",
        durationMin: 32,
        distanceKm: 4.85,
        avgHr: 148,
        rpe: 6,
        dumbbellItems: [],
      })
    )
  );

  // Read single & list subcollection
  await assertSucceeds(docRef.get());
  await assertSucceeds(workoutsCol.get());

  // Partial update mirroring Health Connect upsertSyncedSessions metrics update
  await assertSucceeds(
    hcDocRef.update({
      durationMin: 35,
      distanceKm: 5.1,
      avgHr: 151,
      notes: "Lari pagi santai",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Full editable update mirroring FirebaseRepository.updateWorkoutSession (preserving userId & createdAt)
  await assertSucceeds(
    docRef.update({
      sportType: "DUMBBELL",
      startedAtIso: "2026-11-22T07:30:00Z",
      durationMin: 30,
      rpe: 7,
      notes: null,
      dumbbellItems: [
        { exerciseName: "Push-up", weightKg: 0.0, sets: 4, reps: 10 },
      ],
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject immutable field mutation during update
  await assertFails(
    docRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Delete own workout
  await assertSucceeds(docRef.delete());
});

test("Workout validation rejects invalid fields, @DocumentId 'id', future timestamps, and spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const workoutsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("workouts");

  // Reject redundant 'id' field (guards against @DocumentId CustomClassMapper crash)
  await assertFails(
    workoutsCol.doc("workout-with-id").set(
      validWorkoutPayload(ALICE_UID, { id: "workout-with-id" })
    )
  );

  // Reject ghost field
  await assertFails(
    workoutsCol.doc("workout-ghost").set(
      validWorkoutPayload(ALICE_UID, { isVerified: true })
    )
  );

  // Reject spoofed userId
  await assertFails(
    workoutsCol.doc("workout-spoof").set(validWorkoutPayload(BOB_UID))
  );

  // Reject invalid sportType enum casing
  await assertFails(
    workoutsCol.doc("workout-bad-sport").set(
      validWorkoutPayload(ALICE_UID, { sportType: "running" })
    )
  );

  // Reject out-of-range RPE and durationMin
  await assertFails(
    workoutsCol.doc("workout-bad-rpe").set(
      validWorkoutPayload(ALICE_UID, { rpe: 11 })
    )
  );
  await assertFails(
    workoutsCol.doc("workout-bad-duration").set(
      validWorkoutPayload(ALICE_UID, { durationMin: 0 })
    )
  );

  // Reject tampered future timestamp
  await assertFails(
    workoutsCol.doc("workout-future").set(
      validWorkoutPayload(ALICE_UID, {
        createdAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Reject oversized dumbbellItems list (> 30)
  const tooManyItems = Array.from({ length: 31 }, (_, i) => ({
    exerciseName: `Move ${i}`,
    weightKg: 5,
    sets: 3,
    reps: 10,
  }));
  await assertFails(
    workoutsCol.doc("workout-too-many").set(
      validWorkoutPayload(ALICE_UID, { dumbbellItems: tooManyItems })
    )
  );

  // Reject invalid document ID characters
  await assertFails(
    workoutsCol.doc("bad id!@#").set(validWorkoutPayload(ALICE_UID))
  );
});

test("Cross-user access to another user's workouts is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("workouts")
      .doc("bob-workout-1")
      .set(validWorkoutPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobWorkoutsCol = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("workouts");

  await assertFails(bobWorkoutsCol.get());
  await assertFails(bobWorkoutsCol.doc("bob-workout-1").get());
  await assertFails(
    bobWorkoutsCol.doc("bob-workout-1").update({
      durationMin: 99,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobWorkoutsCol.doc("bob-workout-1").delete());
  await assertFails(
    bobWorkoutsCol.doc("alice-inject").set(validWorkoutPayload(ALICE_UID))
  );
});
