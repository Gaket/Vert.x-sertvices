function displayStatus(status) {
  if (status == 404) {
    showToast("User was not found")
  } else if (status == 204) {
    showToast("User succesfully removed")
  } else if (status == 403) {
    showToast("Only qa team users can be removed")
  }
}

function showToast(text) {
  toast.innerHTML = text;
  toast.classList.remove("off");

  window.setTimeout(hideToast, 5000);
}

function hideToast() {
  toast.classList.add("off");
  toast.innerHTML = ".";
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
  xhttp.open("DELETE", "/users/" + number, true);
  xhttp.send();
}

const searchInput = document.querySelector('.search');
const deleteButton = document.querySelector('.btn');
const toast = document.querySelector('.toast');

searchInput.onkeypress = function (e) {
  if (!e) e = window.event;
  var keyCode = e.keyCode || e.which;
  if (keyCode == '13') {
    removeNumber()
  }
}

deleteButton.onclick = function () { removeNumber() };