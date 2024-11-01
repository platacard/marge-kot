import "https://cdn.jsdelivr.net/npm/jquery@3.7.1/dist/jquery.min.js"
import "https://cdn.jsdelivr.net/npm/fomantic-ui@2.9.3/dist/semantic.min.js"

window.handler = async function () {
  $.toast({
    displayTime: 3000,
    class: "success",
    showIcon: "copy",
    message: "Text copied to clipboard"
  });
}