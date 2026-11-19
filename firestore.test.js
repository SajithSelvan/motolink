const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_cb350rs";
const BOB_UID = "bob_rider";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated users cannot read or write any MotoLink RS collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("bikes").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("serviceRecords").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("diagnosticLogs").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bikes")
      .doc("cb350rs_primary")
      .set({
        userId: ALICE_UID,
        modelName: "Honda CB350RS",
        nickname: "Matte Gunmetal RS",
        modelYear: 2024,
        odometerKm: 4250,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("BikeProfile: owner can create, read, list, update odometer/BT pairing; rejects extra/id fields and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const bikeRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("bikes")
    .doc("cb350rs_primary");

  await assertSucceeds(
    bikeRef.set({
      userId: ALICE_UID,
      modelName: "Honda CB350RS",
      nickname: "Redline RS",
      modelYear: 2024,
      plateNumber: "KA01RS3500",
      odometerKm: 4250,
      pairedDeviceName: "CB350RS-BT",
      pairedDeviceAddress: "AA:BB:CC:11:22:33",
      homeAddress: "Indiranagar, Bengaluru",
      serviceCenterName: "Honda BigWing Topline",
      createdAt: created,
      updatedAt: created,
    })
  );

  // List and Get succeed for owner
  await assertSucceeds(bikeRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bikes").get()
  );

  // Partial update of odometer & pairing succeeds
  await assertSucceeds(
    bikeRef.update({
      odometerKm: 4380,
      pairedDeviceName: "OBDII-ELM327",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    bikeRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow/ghost field rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bikes")
      .doc("cb350rs_ghost")
      .set({
        id: "cb350rs_ghost",
        userId: ALICE_UID,
        modelName: "Honda CB350RS",
        nickname: "Ghost",
        modelYear: 2024,
        odometerKm: 100,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bikes")
      .doc("cb350rs_future")
      .set({
        userId: ALICE_UID,
        modelName: "Honda CB350RS",
        nickname: "Future",
        modelYear: 2024,
        odometerKm: 100,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Cross-user access denied
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bikes")
      .doc("cb350rs_primary")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bikes")
      .doc("cb350rs_primary")
      .update({ odometerKm: 99999, updatedAt: new Date(Date.now() - 1000) })
  );
});

test("ServiceRecord: owner CRUD, category enum validation, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const recRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("serviceRecords")
    .doc("srv_1");

  await assertSucceeds(
    recRef.set({
      userId: ALICE_UID,
      title: "6,000 km Synthetic Oil & Filter",
      category: "ENGINE_OIL",
      odometerKm: 6050,
      costAmount: 1850.5,
      serviceDate: "2026-11-10",
      workshopName: "Honda BigWing",
      notes: "10W-30 fully synthetic JASO MA2",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("serviceRecords")
      .orderBy("odometerKm", "desc")
      .get()
  );

  // Partial update succeeds
  await assertSucceeds(
    recRef.update({
      costAmount: 1920.0,
      notes: "Updated invoice total with gasket washer",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid category fails
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("serviceRecords")
      .doc("srv_bad")
      .set({
        userId: ALICE_UID,
        title: "Invalid Category",
        category: "UNKNOWN_TYPE",
        odometerKm: 6050,
        costAmount: 500,
        serviceDate: "2026-11-10",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user delete fails
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("serviceRecords")
      .doc("srv_1")
      .delete()
  );

  // Owner delete succeeds
  await assertSucceeds(recRef.delete());
});

test("DiagnosticLog: owner create/list/delete, bounds validation, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const diagRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("diagnosticLogs")
    .doc("diag_1");

  await assertSucceeds(
    diagRef.set({
      userId: ALICE_UID,
      source: "BLUETOOTH_OBD",
      batteryVoltage: 12.8,
      engineTempC: 88,
      rpmPeak: 4200,
      odometerKm: 6120,
      dtcCodes: [],
      status: "HEALTHY",
      notes: "Post-ride OBD-II check clear",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Fault log with DTC code succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("diagnosticLogs")
      .doc("diag_2")
      .set({
        userId: ALICE_UID,
        source: "MANUAL_CHECK",
        batteryVoltage: 11.7,
        engineTempC: 92,
        rpmPeak: 1400,
        odometerKm: 6125,
        dtcCodes: ["P0562"],
        status: "WARNING",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("diagnosticLogs")
      .orderBy("createdAt", "desc")
      .get()
  );

  // Out-of-bounds battery voltage rejected
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("diagnosticLogs")
      .doc("diag_bad_v")
      .set({
        userId: ALICE_UID,
        source: "BLUETOOTH_OBD",
        batteryVoltage: 99.0,
        engineTempC: 85,
        rpmPeak: 1500,
        odometerKm: 6120,
        dtcCodes: [],
        status: "HEALTHY",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user read rejected
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("diagnosticLogs")
      .doc("diag_1")
      .get()
  );
});
