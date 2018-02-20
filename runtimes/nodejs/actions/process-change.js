function main(params) {
  return new Promise(function(resolve, reject) {
    if (!params.name || !params.color) {
      reject({
        'error': 'Please make sure name and color are passed in as params.'
      });
      return;
    } else {
      var message = 'A ' + params.color + ' cat named ' + params.name + ' was added.';
      console.log(message);
      resolve({
        change: message
      });
      return;
    }
  });
}
