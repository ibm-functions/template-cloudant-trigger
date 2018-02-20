function main(params) {
  const { name, color } = params;
  return new Promise((resolve, reject) => {
    if (!name || !color) {
      reject({
        error: 'Please make sure name and color are passed in as params.'
      });
    } else {
      const message = `A ${color} cat named ${name} was added`;
      console.log(message);
      resolve({
        change: message,
      });
    }
  });
}
exports.main = main;
