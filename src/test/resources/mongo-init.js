db1 = new Mongo().getDB("payroll");
db1.createUser({
    user: "admin",
    pwd: "docker",
    roles: [{ role: "readWrite", db: "payroll" }]
});

db2 = new Mongo().getDB("proquore");
db2.createUser({
    user: "admin",
    pwd: "docker",
    roles: [{ role: "readWrite", db: "proquore" }]
});
