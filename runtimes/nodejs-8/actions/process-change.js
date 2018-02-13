function main({name, color}) {
  return new Promise(function(resolve, reject) {
    if (!name || !color) {
      reject({
        'error': 'Please make sure name and color are passed in as params.'
      });
      return;
    } else {
      var message = 'A ' + color + ' cat named ' + name + ' was added.';
      console.log(message);
      resolve({
        change: message
      });
      return;
    }
  });
}
