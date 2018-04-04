var Cloudant = require("cloudant");

function main(args){
  var username = args.username
  var password = args.password
  var dbName = args.dbName
  var create = args.create
  var cloudant = Cloudant({account:username, password:password, plugin:'promises'});

  if (create == 'create') {
    return cloudant.db.create(dbName)
  } else {
    return cloudant.db.destroy(dbName)
  }
}
