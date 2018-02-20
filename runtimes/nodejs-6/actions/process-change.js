function main(params) {
  return new Promise((resolve, reject) => {
    if (!params.name || !params.color) {
      reject({
        error: 'Please make sure name and color are passed in as params.'
      });
    } else {
      const message = `A ${params.color} cat named ${params.name} was added`;
      console.log(message);
      resolve({
        change: message
      });
    }
  });
}
exports.main = main;
