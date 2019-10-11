const searchInput = document.querySelector('.search');
const deleteDevButton = document.querySelector('#dev');
const deleteProdButton = document.querySelector('#prod');
const toast = document.querySelector('.toast');

function hideToast() {
  toast.classList.add("off");
  toast.innerHTML = ".";
}

function moveToLogin() {
  window.location.href = "/login";
}

function showToast(text) {
  toast.textContent = text;
  toast.classList.remove("off");

  window.setTimeout(hideToast, 5000);
}

function displayStatus(status) {
  if (status == 404) {
    showToast("User was not found")
  } else if (status == 401) {
    showToast("Please, login... Redirecting in 2 seconds")
    window.setTimeout(moveToLogin, 2000);
  } else if (status == 204) {
    showToast("User succesfully removed")
  } else if (status == 403) {
    showToast("Only qa team users can be removed")
  } else {
    showToast("Unexpected error")
  }
}

function removeNumber(server) {

  if (deleteDevButton.disabled) {
    return;
  }

  deleteDevButton.disabled = true;
  deleteProdButton.disabled = true;
  searchInput.disabled = true;

  var xhttp = new XMLHttpRequest();
  xhttp.onreadystatechange = function () {
    if (this.readyState == 4) {
      displayStatus(this.status);
      deleteDevButton.disabled = false;
      deleteProdButton.disabled = false;
      searchInput.disabled = false;
      searchInput.value = "";
    }
  }

  const number = "+" + searchInput.value.replace(/\D/g,'');
  const params = server ? "server=" + server : "";
  xhttp.open("DELETE", "/users/phone/" + number +"?" + params, true);
  xhttp.send();
}

searchInput.onkeypress = function (e) {
  if (!e) e = window.event;
  var keyCode = e.keyCode || e.which;
  if (keyCode == '13') {
    removeNumber()
  }
}

deleteDevButton.onclick = function () { removeNumber() };
deleteProdButton.onclick = function () { removeNumber('prod') };