const searchInput = document.querySelector('.search');
const deleteButton = document.querySelector('.btn');
const toast = document.querySelector('.toast');

function hideToast() {
  toast.classList.add("off");
  toast.innerHTML = ".";
}

function moveToLogin() {
  window.location.href = "/login";
}

function showToast(text) {
  toast.innerHTML = `${text}`;
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

function removeNumber() {

  if (deleteButton.disabled) {
    return;
  }

  deleteButton.disabled = true;
  searchInput.disabled = true;

  var xhttp = new XMLHttpRequest();
  xhttp.onreadystatechange = function () {
    if (this.readyState == 4) {
      displayStatus(this.status);
      deleteButton.disabled = false;
      searchInput.disabled = false;
      searchInput.value = "";
    }
  }

  const number = "+" + searchInput.value.replace(/\D/g,'');
  xhttp.open("DELETE", "/users/phone/" + number, true);
  xhttp.send();
}

searchInput.onkeypress = function (e) {
  if (!e) e = window.event;
  var keyCode = e.keyCode || e.which;
  if (keyCode == '13') {
    removeNumber()
  }
}

deleteButton.onclick = function () { removeNumber() };